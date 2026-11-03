package ProjetoJuizoFinal;
import robocode.AdvancedRobot;
import robocode.*;
import robocode.util.Utils;
import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Map;
// API help : https://robocode.sourceforge.io/docs/robocode/robocode/Robot.html
/**
 * JuizoFinal - a robot by (your name here)
 */
public class JuizoFinal extends AdvancedRobot
{
    // guarda os dados de cada inimigo já escaneado (chave = nome do inimigo)
    Map<String, Inimigo> inimigos = new HashMap<String, Inimigo>();
    // nome do inimigo escolhido como alvo (null = nenhum)
    String alvo = null;
	int sentido = 1;

    /**
     * run: JuizoFinal's default behavior
     */
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

            // radar acompanha o alvo (e varre a arena de tempos em tempos)
            controlarRadar();
 
            // mesmo movimento de antes (frente 100 / trás 100), mas sem travar o loop
            if (getDistanceRemaining() == 0) {
                setAhead(100 * sentido);
                sentido = -sentido;
            }
 
            // a cada tick, o canhão acompanha o alvo
            mirarEAtirar();
            execute();
     	}
	 }
	 
	public void mirarEAtirar() {
    if (alvo == null) return;
    Inimigo i = inimigos.get(alvo);
    if (i == null || !recente(i)) return;

    // onde o inimigo está AGORA (andou desde o último scan)
    long atraso = getTime() - i.visto;
    double px = limitar(i.x + Math.sin(i.rumo) * i.velocidade * atraso, getBattleFieldWidth());
    double py = limitar(i.y + Math.cos(i.rumo) * i.velocidade * atraso, getBattleFieldHeight());

    // potência conforme a distância
    double distancia = Point2D.distance(getX(), getY(), px, py);
    double potencia;
    if      (distancia < 150) potencia = 3.0;
    else if (distancia < 400) potencia = 2.0;
    else                      potencia = 1.2;
    potencia = Math.min(potencia, getEnergy() - 0.1);
    if (potencia < 0.1) return;

    double velocidadeBala = 20 - 3 * potencia;

    // simula o inimigo andando até a bala chegar (mira linear)
    for (int t = 1; t < 200; t++) {
        if (t * velocidadeBala >= Point2D.distance(getX(), getY(), px, py)) break;
        px = limitar(px + Math.sin(i.rumo) * i.velocidade, getBattleFieldWidth());
        py = limitar(py + Math.cos(i.rumo) * i.velocidade, getBattleFieldHeight());
    }

    // gira o canhão até o ponto previsto
    double anguloTiro = Math.atan2(px - getX(), py - getY());
    setTurnGunRightRadians(Utils.normalRelativeAngle(anguloTiro - getGunHeadingRadians()));

    // atira quando o canhão está frio, alinhado e com dados recentes
    double tolerancia = Math.atan(18 / Math.max(distancia, 1));
    if (getGunHeat() == 0 && atraso <= 10
            && Math.abs(getGunTurnRemainingRadians()) < tolerancia) {
        setFire(potencia);
    }
}

// mantém a posição prevista dentro da arena
double limitar(double v, double max) {
    return Math.max(18, Math.min(max - 18, v));
}

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
    /**
     * onScannedRobot: What to do when you see another robot
     */
    public void onScannedRobot(ScannedRobotEvent e) {
        // guarda/atualiza os dados desse inimigo
        Inimigo i = inimigos.get(e.getName());
        if (i == null) {
            i = new Inimigo();
            i.nome = e.getName();
            inimigos.put(e.getName(), i);
        }
        double anguloAbsoluto = getHeadingRadians() + e.getBearingRadians();
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
    /**
     * onRobotDeath: remove o inimigo morto da lista
     */
    public void onRobotDeath(RobotDeathEvent e) {
        inimigos.remove(e.getName());
        if (e.getName().equals(alvo)) {
            alvo = null;
        }
    }
    /**
     * onHitByBullet: What to do when you're hit by a bullet
     */
    public void onHitByBullet(HitByBulletEvent e) {
        // Replace the next line with any behavior you would like
        back(10);
    }
    /**
     * onHitWall: What to do when you hit a wall
     */
    public void onHitWall(HitWallEvent e) {
        // Replace the next line with any behavior you would like
        back(20);
    }    
}