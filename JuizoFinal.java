package ProjetoJuizoFinal;
import robocode.AdvancedRobot;
import robocode.*;
import robocode.util.Utils;
import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Map;


public class JuizoFinal extends AdvancedRobot
{
    // guarda os dados de cada inimigo já escaneado (chave = nome do inimigo)
    Map<String, Inimigo> inimigos = new HashMap<String, Inimigo>();
    // nome do inimigo escolhido como alvo (null = nenhum)
    String alvo = null;
	int sentido = 1;

    // canto escolhido para o começo da partida
    Point2D.Double canto = null;
    // se tem mais inimigos q isso vivos, robô fica no cantinho
    static final int LIMITE_MELEE = 2;
    // distância do ponto do canto até as paredes
    static final double RECUO_CANTO = 170;
    // distância ideal do inimigo mais próximo (no 1x1 ele fica perto, no melee ele fica longe)
    static final double DIST_PERTO = 180;
    static final double DIST_LONGE = 400;

    
    // *** run *** //
    public void run() {
    // aqui to só definindo as cores do robô
    setGunColor(new Color(240, 208, 221));
    setBodyColor(new Color(240, 208, 221));
    setRadarColor(new Color(240, 208, 221));
	
        // corpo, canhão e radar giram de forma independente
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setAdjustRadarForRobotTurn(true);
 
        while(true) {
            controlarRadar();

            // olha 80 px à frente no sentido que tá andando;
            // se for bater na parede, vai pro meio
            double olhar = 80 * Math.signum(getDistanceRemaining());
            if (olhar != 0 && getTurnRemaining() == 0 && pertoDaParede(olhar)) {
                fugirProMeio(150);
            }
            // movimento de vai e vem
            else if (getDistanceRemaining() == 0) {
                vaiPraOnde();
            }
            
            // a cada tick, o canhão acompanha o alvo
            mirarEAtirar();
            execute();
        }
	 }


    // *** mirar e atirar *** //
	public void mirarEAtirar() {
        if (alvo == null) return;
        Inimigo i = inimigos.get(alvo);
        if (i == null || !recente(i)) return;

        // onde o inimigo está AGORA (andou, e talvez girou, desde o último scan)
        long atraso = getTime() - i.visto;
        double px = i.x, py = i.y, rumo = i.rumo;
        for (long k = 0; k < atraso; k++) {
            rumo += i.taxaGiro;
            px = limitar(px + Math.sin(rumo) * i.velocidade, getBattleFieldWidth());
            py = limitar(py + Math.cos(rumo) * i.velocidade, getBattleFieldHeight());
        }

        // potência conforme a distância
        double distancia = Point2D.distance(getX(), getY(), px, py);
        double potencia;
        if      (distancia < 150) potencia = 3.0;
        else if (distancia < 400) potencia = 2.0;
        else                      potencia = 1.2;
        // não gasta mais energia do que o necessário para matar o alvo
        // (dano = 4 x potência, mais 2 x (potência - 1) quando a potência passa de 1)
        double necessaria = (i.energia <= 4) ? i.energia / 4 : (i.energia + 2) / 6;
        potencia = Math.min(potencia, Math.max(necessaria, 0.1));
        // com pouca energia, só tiros fracos
        if (getEnergy() < 20) potencia = Math.min(potencia, 1.0);
        // nunca gasta a energia toda
        potencia = Math.min(potencia, getEnergy() - 0.1);
        if (potencia < 0.1) return;

        double velocidadeBala = 20 - 3 * potencia;

        // simula o inimigo andando (e girando) até a bala chegar (mira circular)
        for (int t = 1; t < 200; t++) {
            if (t * velocidadeBala >= Point2D.distance(getX(), getY(), px, py)) break;
            rumo += i.taxaGiro;
            px = limitar(px + Math.sin(rumo) * i.velocidade, getBattleFieldWidth());
            py = limitar(py + Math.cos(rumo) * i.velocidade, getBattleFieldHeight());
        }

        // gira o canhão até o ponto previsto
        double anguloTiro = Math.atan2(px - getX(), py - getY());
        setTurnGunRightRadians(Utils.normalRelativeAngle(anguloTiro - getGunHeadingRadians()));

        // atira quando o canhão está frio, alinhado, com dados recentes e o alvo não está longe demais
        double tolerancia = Math.atan(18 / Math.max(distancia, 1));
        if (getGunHeat() == 0 && atraso <= 10 && distancia < 700
                && Math.abs(getGunTurnRemainingRadians()) < tolerancia) {
            setFire(potencia);
        }
    }

    // mantém a posição prevista dentro da arena
    double limitar(double v, double max) {
        return Math.max(18, Math.min(max - 18, v));
    }

    // *** controlar radar *** //
    // radar: segue o alvo e, de tempos em tempos, varre a arena inteira
    public void controlarRadar() {
        Inimigo a = (alvo == null) ? null : inimigos.get(alvo);

        // com mais de 1 inimigo, a cada 24 ticks gira por 9 ticks para ver todos
        boolean varreduraCompleta = (getOthers() > 1) && (getTime() % 24 < 9);
        // com mais de 3 inimigos vivos, o radar só gira (vê todo mundo a cada 8 ticks)
        boolean muitosInimigos = getOthers() > 3;

        // sem alvo, alvo sem aparecer há muito tempo, hora da varredura ou muitos inimigos: gira sem parar
        if (a == null || getTime() - a.visto > 12 || varreduraCompleta || muitosInimigos) {
            setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
            return;
        }

        // senão: aponta o radar para o alvo, passando um pouco dele para não perdê-lo
        double anguloAlvo = Math.atan2(a.x - getX(), a.y - getY());
        double giro = Utils.normalRelativeAngle(anguloAlvo - getRadarHeadingRadians());
        setTurnRadarRightRadians(giro + (giro >= 0 ? 0.35 : -0.35));
    }


    // *** scanned robot *** //
    public void onScannedRobot(ScannedRobotEvent e) {
        // guarda/atualiza os dados desse inimigo
        Inimigo i = inimigos.get(e.getName());
        if (i == null) {
            i = new Inimigo();
            i.nome = e.getName();
            i.rumo = e.getHeadingRadians();
            i.visto = getTime();
	    i.energia = e.getEnergy();
            inimigos.put(e.getName(), i);
        }
	// esquiva reativa de tiro
	// se a energia do robô inimigo caiu entre 0.1 e 3.0, significa q ele acabou de atirar
	double quedaEnergia = i.energia - e.getEnergy();
	if (quedaEnergia > 0.09 && quedaEnergia <= 3.0){
	// inverte o sentido imediatamente, e cancela o trajeto antigo
	sentido = -sentido;
	setAhead(0); // para o movimento atual, e recalcula no proximo tick
	}

        double anguloAbsoluto = getHeadingRadians() + e.getBearingRadians();
        // quanto o inimigo girou por tick desde o último scan (para a mira circular)
        long dt = getTime() - i.visto;
        if (dt > 0 && dt <= 8) {
            double giro = Utils.normalRelativeAngle(e.getHeadingRadians() - i.rumo) / dt;
            i.taxaGiro = Math.max(-0.18, Math.min(0.18, giro)); // limite do jogo: ~10 graus por tick
        } else if (dt > 8) {
            i.taxaGiro = 0;
        }
        i.x = getX() + Math.sin(anguloAbsoluto) * e.getDistance();
        i.y = getY() + Math.cos(anguloAbsoluto) * e.getDistance();
        i.energia = e.getEnergy();
        i.velocidade = e.getVelocity();
        i.rumo = e.getHeadingRadians();
        i.visto = getTime();

        // reavalia quem é o alvo com os dados mais recentes
        escolherAlvo();

       
    }


    static class Inimigo {
    String nome;
    double x, y, energia, velocidade,rumo;
    double taxaGiro; // quanto o rumo do inimigo muda por tick (radianos)
    long visto; // ultima vez q foi escaneado
    }
    // considera "recente" o inimigo visto nos últimos 40 ticks
    boolean recente(Inimigo i) {
        return getTime() - i.visto <= 40;
    }
    // pontuação do inimigo: quanto MENOR, melhor alvo (pouca energia e perto)
    double pontuacao(Inimigo i) {
        double distancia = Point2D.distance(getX(), getY(), i.x, i.y);
        return i.energia + distancia * 0.1; // 100 px de distância "valem" 10 de energia
    }
    // escolhe como alvo o inimigo com a menor pontuação (energia + distância)
    public void escolherAlvo () {
        Inimigo melhor = null;
        double menor = Double.MAX_VALUE;
        for (Inimigo i : inimigos.values()) {
            if (!recente(i)) continue;
            double p = pontuacao(i);
            if (p < menor) {
                menor = p;
                melhor = i;
            }
        }
        if (melhor == null) {
            alvo = null;
            return;
        }
        // só troca de alvo se o novo for bem melhor que o atual (evita ficar trocando)
        Inimigo atual = (alvo == null) ? null : inimigos.get(alvo);
        if (atual != null && recente(atual) && pontuacao(atual) - menor < 15) {
            return;
        }
        alvo = melhor.nome;
    }
    

    // *** onRobotDeath *** //
    // remove o inimigo morto da lista
    public void onRobotDeath(RobotDeathEvent e) {
        inimigos.remove(e.getName());
        if (e.getName().equals(alvo)) {
            alvo = null;
        }
    }
    

    // *** onHitByBullet *** //
    public void onHitByBullet(HitByBulletEvent e) {
        // o movimento não reage mais ao tiro (fica tudo no proximoPasso)
        // se não tem alvo, revida em quem atirou
        if (alvo == null && inimigos.containsKey(e.getName())) {
            alvo = e.getName();
        }
    }

    // *** onBulletHit *** //
    // acionado quando o NOSSO tiro acerta um inimigo
    public void onBulletHit(BulletHitEvent e) {
        Inimigo i = inimigos.get(e.getName());
        if (i != null) {
            i.energia = e.getEnergy(); // atualiza a energia para o robô não achar que o inimigo atirou
        }
    }

    // distância mínima que o robô tenta manter das paredes
    static final double MARGEM = 60;

    // *** pertoDaParede *** //
    boolean pertoDaParede(double dist) {
        // checa se está muito perto da parede
        // (dist negativo = olha para trás)
        double x = getX() + Math.sin(getHeadingRadians()) * dist;
        double y = getY() + Math.cos(getHeadingRadians()) * dist;
        return x < MARGEM || y < MARGEM
            || x > getBattleFieldWidth() - MARGEM
            || y > getBattleFieldHeight() - MARGEM;
    }


    // *** fugirProMeio *** //
    public void fugirProMeio(double dist) {
        // vira pro meio da arena e anda até lá
        // se for mais rápido, vai de ré
        double anguloCentro = Math.atan2(getBattleFieldWidth() / 2 - getX(), getBattleFieldHeight() / 2 - getY());
        double giro = Utils.normalRelativeAngle(anguloCentro - getHeadingRadians());
        if (Math.abs(giro) > Math.PI / 2) {
            giro = Utils.normalRelativeAngle(giro + Math.PI);
            dist = -dist;
        }
        setTurnRightRadians(giro);
        setAhead(dist);
    }
    

    // *** vaiPraOnde *** //
    // decide o próximo vai/volta do vai-e-vem
    public void vaiPraOnde() {
        Inimigo perto = maisProximo();

        // não viu ninguém: vai-e-vem
        if (perto == null) {
            setAhead(100 * sentido);
            sentido = -sentido;
            return;
        }

        // muitos inimigos (começo): cantinho
        if (getOthers() > LIMITE_MELEE) {
            escolherCanto();
            double dCanto = Point2D.distance(getX(), getY(), canto.x, canto.y);
            // longe do canto: vai até ele
            if (dCanto > 80) {
                irPara(Math.atan2(canto.x - getX(), canto.y - getY()), Math.min(dCanto, 180));
                return;
            }
            // no canto: vai-e-vem curto
            double anguloCentro = Math.atan2(getBattleFieldWidth() / 2 - canto.x, getBattleFieldHeight() / 2 - canto.y);
            irPara(anguloCentro + sentido * Math.PI / 2, 50 + Math.random() * 20);
            sentido = -sentido;
            return;
        }

        // poucos inimigos: vai-e-vem de lado em relação ao inimigo mais próximo,
        // inclinado para chegar (ou voltar) à distância ideal
        double anguloInimigo = Math.atan2(perto.x - getX(), perto.y - getY());
        double dist = Point2D.distance(getX(), getY(), perto.x, perto.y);
        double ideal = distanciaIdeal();
        // erro > 0: longe demais (inclina para perto); erro < 0: perto demais (inclina para longe)
        double erro = Math.max(-1, Math.min(1, (dist - ideal) / ideal));
        double correcao = erro * 0.6; // no máximo ~35°
        // tamanho variado para o vai-e-vem não ficar previsível
	double comprimento = 50 + Math.random() * 50;
        irPara(anguloInimigo + sentido * (Math.PI / 2 - correcao), comprimento);
        sentido = -sentido;
    }

    // *** distanciaIdeal *** //
    // quanto menos inimigos vivos, mais perto
    double distanciaIdeal() {
        double fracao = (getOthers() - 1) / (double) (LIMITE_MELEE - 1);
        fracao = Math.max(0, Math.min(1, fracao));
        return DIST_PERTO + (DIST_LONGE - DIST_PERTO) * fracao;
    }

    // *** maisProximo *** //
    // inimigo recente mais perto de mim (é dele que o robô mantém distância)
    Inimigo maisProximo() {
        Inimigo melhor = null;
        double menor = Double.MAX_VALUE;
        for (Inimigo i : inimigos.values()) {
            if (!recente(i)) continue;
            double d = Point2D.distance(getX(), getY(), i.x, i.y);
            if (d < menor) {
                menor = d;
                melhor = i;
            }
        }
        return melhor;
    }

    // *** escolherCanto *** //
    // escolhe o canto com menos perigo; só troca se o novo for bem melhor (tipo escolherAlvo)
    void escolherCanto() {
        double w = getBattleFieldWidth(), h = getBattleFieldHeight();
        double[][] cantos = { {RECUO_CANTO, RECUO_CANTO}, {w - RECUO_CANTO, RECUO_CANTO},
                              {RECUO_CANTO, h - RECUO_CANTO}, {w - RECUO_CANTO, h - RECUO_CANTO} };
        Point2D.Double melhor = null;
        double menor = Double.MAX_VALUE;
        for (double[] c : cantos) {
            double p = perigoCanto(c[0], c[1]);
            if (p < menor) {
                menor = p;
                melhor = new Point2D.Double(c[0], c[1]);
            }
        }
        if (canto == null || menor < perigoCanto(canto.x, canto.y) * 0.7) {
            canto = melhor;
        }
    }

    // perigo de um canto: inimigos perto dele pesam muito (1/d²),
    // e canto longe de mim pesa mais (teria que atravessar a matança do meio)
    double perigoCanto(double x, double y) {
        double perigo = 0;
        for (Inimigo i : inimigos.values()) {
            if (!recente(i)) continue;
            double d = Point2D.distance(x, y, i.x, i.y);
            perigo += 1 / Math.max(d * d, 1);
        }
        return perigo * (1 + Point2D.distance(getX(), getY(), x, y) / 400);
    }

    // *** irPara *** //
    // vira para um ângulo e anda 'dist'; se for mais rápido, vai de ré (igual ao fugirProMeio)
    void irPara(double angulo, double dist) {
        double giro = Utils.normalRelativeAngle(angulo - getHeadingRadians());
        if (Math.abs(giro) > Math.PI / 2) {
            giro = Utils.normalRelativeAngle(giro + Math.PI);
            dist = -dist;
        }
        setTurnRightRadians(giro);
        setAhead(dist);
    }

    // *** onHitWall *** //
    public void onHitWall(HitWallEvent e) {
    // quando bater na parede, chama o método fugirProMeio
    fugirProMeio(150);
    }    
}
