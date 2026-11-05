package ProjetoJuizoFinal;
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

    // se tem mais inimigos q isso vivos, é considerado melee
    static final int LIMITE_MELEE = 2;
    // distância ideal do inimigo mais próximo (no 1x1 ele fica perto, no melee ele fica longe)
    static final double DIST_PERTO = 250;
    static final double DIST_LONGE = 400;

    // *** parâmetros do movimento anti-gravidade *** //
    static final double PESO_INIMIGO = 40000;   // repulsão = PESO / d²
    static final double PESO_PAREDE = 20000;    // repulsão da parede = PESO / d²
    static final double PESO_TANGENCIAL = 1.0;  // força que faz o robô circular o inimigo
    static final double PESO_RADIAL = 1.0;      // força que ajusta a distância ideal
    static final double MARGEM_PAREDE = 40;     // margem do "wall smoothing"
    static final double OLHAR_PAREDE = 140;     // quanto olha à frente pra não bater
    static final long INTERVALO_ESQUIVA = 8;    // ticks mínimos entre duas esquivas
    long ultimaEsquiva = -100;
    long proximaTroca = 0;                      // troca de lado aleatória (imprevisível)

    // *** parâmetros da fuga de colisão *** //
    static final long DURACAO_FUGA = 14;        // ticks andando pra longe depois de bater em alguém
    static final int LIMITE_PARADO = 6;         // ticks quase parado = considera que travou
    long fugaAte = -1;                          // até que tick o modo fuga fica ativo
    double anguloFuga = 0;                      // direção da fuga (radianos)
    int ticksParado = 0;                        // quantos ticks seguidos o robô está quase parado


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
            // movimento contínuo, recalculado a cada tick
            mover();
            // a cada tick, o canhão acompanha o alvo
            mirarEAtirar();
            execute();
        }
    }


    // *** mover (anti-gravidade) *** //
    // soma forças: inimigos e paredes empurram o robô; uma força lateral faz ele
    // circular o inimigo mais próximo; outra ajusta a distância ideal.
    public void mover() {
        double w = getBattleFieldWidth(), h = getBattleFieldHeight();
        double fx = 0, fy = 0;
        Inimigo perto = maisProximo();
        boolean duelo = getOthers() <= LIMITE_MELEE;

        // 0) fuga de colisão: enquanto estiver ativa, ignora a anti-gravidade e só se afasta
        if (getTime() < fugaAte) {
            irPara(suavizarParede(anguloFuga), 150);
            return;
        }

        // detector de travamento: se o robô ficou quase parado por vários ticks seguidos,
        // força uma fuga (pra longe do inimigo mais próximo, ou pro centro se não vê ninguém)
        if (Math.abs(getVelocity()) < 0.5) ticksParado++;
        else ticksParado = 0;
        if (ticksParado > LIMITE_PARADO) {
            ticksParado = 0;
            if (perto != null) {
                anguloFuga = Math.atan2(getX() - perto.x, getY() - perto.y) + sentido * Math.PI / 6;
            } else {
                anguloFuga = Math.atan2(w / 2 - getX(), h / 2 - getY());
            }
            fugaAte = getTime() + DURACAO_FUGA;
            irPara(suavizarParede(anguloFuga), 150);
            return;
        }

        // troca de lado de vez em quando (tempo aleatório) pra não ficar previsível
        if (getTime() >= proximaTroca) {
            sentido = -sentido;
            proximaTroca = getTime() + 15 + (long) (Math.random() * 35);
        }

        // 1) repulsão dos inimigos (1/d²)
        for (Inimigo i : inimigos.values()) {
            if (!recente(i)) continue;
            // no fim da partida, a distância do mais próximo é controlada pelo termo radial
            if (duelo && i == perto) continue;
            double dx = getX() - i.x, dy = getY() - i.y;
            double d2 = Math.max(dx * dx + dy * dy, 1);
            double d = Math.sqrt(d2);
            double forca = PESO_INIMIGO / d2;
            fx += dx / d * forca;
            fy += dy / d * forca;
        }

        // 2) repulsão das paredes (empurra pra dentro da arena)
        fx += PESO_PAREDE / Math.max(getX() * getX(), 1);
        fx -= PESO_PAREDE / Math.max((w - getX()) * (w - getX()), 1);
        fy += PESO_PAREDE / Math.max(getY() * getY(), 1);
        fy -= PESO_PAREDE / Math.max((h - getY()) * (h - getY()), 1);

        // 3) força lateral: circula o inimigo mais próximo (ou o centro, se não vê ninguém)
        double refX = (perto == null) ? w / 2 : perto.x;
        double refY = (perto == null) ? h / 2 : perto.y;
        double anguloRef = Math.atan2(refX - getX(), refY - getY());
        double tangente = anguloRef + sentido * Math.PI / 2;
        fx += Math.sin(tangente) * PESO_TANGENCIAL;
        fy += Math.cos(tangente) * PESO_TANGENCIAL;

        // 4) força radial: aproxima se tá longe demais, afasta se tá perto demais
        if (perto != null) {
            double dist = Point2D.distance(getX(), getY(), perto.x, perto.y);
            double ideal = distanciaIdeal();
            double erro = Math.max(-1, Math.min(1, (dist - ideal) / ideal));
            fx += Math.sin(anguloRef) * erro * PESO_RADIAL;
            fy += Math.cos(anguloRef) * erro * PESO_RADIAL;
        }

        // direção resultante, ajustada pra não bater na parede
        double angulo = Math.atan2(fx, fy);
        angulo = suavizarParede(angulo);
        irPara(angulo, 120);
    }

    // *** suavizarParede *** //
    // se o ponto à frente cair perto da parede, gira o caminho em direção ao centro
    double suavizarParede(double angulo) {
        double w = getBattleFieldWidth(), h = getBattleFieldHeight();
        double centro = Math.atan2(w / 2 - getX(), h / 2 - getY());
        double passo = (Utils.normalRelativeAngle(centro - angulo) >= 0) ? 0.1 : -0.1;
        for (int k = 0; k < 40; k++) {
            double x = getX() + Math.sin(angulo) * OLHAR_PAREDE;
            double y = getY() + Math.cos(angulo) * OLHAR_PAREDE;
            if (x > MARGEM_PAREDE && x < w - MARGEM_PAREDE
                    && y > MARGEM_PAREDE && y < h - MARGEM_PAREDE) {
                break;
            }
            angulo += passo;
        }
        return angulo;
    }

    // *** irPara *** //
    // vira para um ângulo e anda 'dist'; se for mais rápido, vai de ré
    void irPara(double angulo, double dist) {
        double giro = Utils.normalRelativeAngle(angulo - getHeadingRadians());
        if (Math.abs(giro) > Math.PI / 2) {
            giro = Utils.normalRelativeAngle(giro + Math.PI);
            dist = -dist;
        }
        setTurnRightRadians(giro);
        setAhead(dist);
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
        // (limita a frequência: com 9 inimigos atirando, inverter a cada scan viraria ruído)
        double quedaEnergia = i.energia - e.getEnergy();
        if (quedaEnergia > 0.09 && quedaEnergia <= 3.0
                && getTime() - ultimaEsquiva > INTERVALO_ESQUIVA) {
            sentido = -sentido;
            ultimaEsquiva = getTime();
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
        double x, y, energia, velocidade, rumo;
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
    // se não tem alvo, revida em quem atirou
    public void onHitByBullet(HitByBulletEvent e) {
        if (alvo == null && inimigos.containsKey(e.getName())) {
            alvo = e.getName();
        }
    }

    // *** onHitRobot *** //
    // entra em modo fuga: por alguns ticks, anda direto pra longe do robô que bateu,
    // com um leve desvio lateral pra não ficar alinhado com ele
    public void onHitRobot(HitRobotEvent e) {
        // a colisão tira 0.6 de energia do outro robô; atualiza aqui pra que o
        // onScannedRobot não confunda essa queda com um tiro (e fique invertendo o sentido)
        Inimigo i = inimigos.get(e.getName());
        if (i != null) {
            i.energia = e.getEnergy();
        }
        double anguloInimigo = getHeadingRadians() + e.getBearingRadians();
        anguloFuga = anguloInimigo + Math.PI + sentido * Math.PI / 6;
        fugaAte = getTime() + DURACAO_FUGA;
        ticksParado = 0;
    }

    // *** onBulletHit *** //
    // acionado quando o NOSSO tiro acerta um inimigo
    public void onBulletHit(BulletHitEvent e) {
        Inimigo i = inimigos.get(e.getName());
        if (i != null) {
            i.energia = e.getEnergy(); // atualiza a energia para o robô não achar que o inimigo atirou
        }
    }
}