package ProjetoJuizoFinal;
import robocode.*;
import robocode.util.Utils;
import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;


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

        // com mais de LIMITE_MELEE inimigos vivos, usa o Minimum Risk Movement
        // (no fim da partida volta pra anti-gravidade + esquiva, que é melhor no duelo)
        if (!duelo) { moverMRM(); return; }

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


    // *** Minimum Risk Movement *** //
    // sorteia pontos ao redor e vai pro ponto de menor "risco"
    Point2D.Double destino = null, ultimaPos = null;
    boolean repensar = false;
    static final double MARGEM_MRM = 35;

    void moverMRM() {
        double w = getBattleFieldWidth(), h = getBattleFieldHeight();
        Point2D.Double eu = new Point2D.Double(getX(), getY());
        if (destino == null) { destino = eu; ultimaPos = eu; }
        Inimigo perto = maisProximo();
        double dPerto = (perto == null) ? 400 : eu.distance(perto.x, perto.y);
        boolean perto15 = eu.distance(destino) < 15;
        int n = (perto15 || repensar) ? 60 : 8;
        double riscoAtual = (perto15 || repensar) ? Double.MAX_VALUE : risco(destino, eu) * 0.85;
        for (int k = 0; k < n; k++) {
            double raio = Math.min(dPerto * 0.8, 100 + 120 * Math.random());
            double ang = 2 * Math.PI * Math.random();
            double x = getX() + Math.sin(ang) * raio, y = getY() + Math.cos(ang) * raio;
            if (x < MARGEM_MRM || y < MARGEM_MRM || x > w - MARGEM_MRM || y > h - MARGEM_MRM) continue;
            Point2D.Double p = new Point2D.Double(x, y);
            double r = risco(p, eu);
            if (r < riscoAtual) { riscoAtual = r; destino = p; }
        }
        if (perto15 || repensar) ultimaPos = eu;
        repensar = false;
        double ang = Math.atan2(destino.x - getX(), destino.y - getY());
        irPara(ang, eu.distance(destino));
    }

    double risco(Point2D.Double p, Point2D.Double eu) {
        double r = 0.08 / Math.max(p.distanceSq(ultimaPos), 1);
        double ida = Math.atan2(p.x - eu.x, p.y - eu.y);
        for (Inimigo i : inimigos.values()) {
            if (!recente(i)) continue;
            double forca = Math.min(i.energia / Math.max(getEnergy(), 1), 2);
            double angInimigo = Math.atan2(i.x - p.x, i.y - p.y);
            double perp = 1 + Math.abs(Math.cos(ida - angInimigo));
            // se sou o robô mais perto dele, provavelmente ele mira em mim
            int maisPerto = 0;
            double dEu = p.distanceSq(i.x, i.y);
            for (Inimigo j : inimigos.values()) {
                if (j != i && recente(j) && Point2D.distanceSq(j.x, j.y, i.x, i.y) < dEu * 0.9) maisPerto++;
            }
            double mira = (maisPerto == 0) ? 2.0 : 1.0;
            r += forca * perp * mira / Math.max(dEu, 1);
        }
        return r;
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


    // *** mirar e atirar (escolhe o ângulo que tem mais chance de acertar ALGUÉM) *** //
    public void mirarEAtirar() {
        if (alvo == null) return;
        Inimigo a = inimigos.get(alvo);
        if (a == null || !recente(a)) return;
        long atrasoAlvo = getTime() - a.visto;

        // potência definida pelo alvo principal (mesma regra de antes)
        double distAlvo = Point2D.distance(getX(), getY(), a.x, a.y);
        double potencia = potenciaPara(a, distAlvo);
        if (potencia < 0.1) return;
        double velocidadeBala = 20 - 3 * potencia;

        // uma "solução de tiro" por inimigo: ângulo, tolerância, peso
        List<double[]> sol = new ArrayList<double[]>();
        for (Inimigo i : inimigos.values()) {
            if (getTime() - i.visto > 10) continue;
            double[] p = prever(i, velocidadeBala);
            double d = Point2D.distance(getX(), getY(), p[0], p[1]);
            double ang = Math.atan2(p[0] - getX(), p[1] - getY());
            double tol = Math.atan(18 / Math.max(d, 1));
            double peso = 1.0 / Math.max(d, 50);   // inimigo perto vale mais
            if (i == a) peso *= 1.5;               // leve preferência pelo alvo escolhido
            sol.add(new double[] { ang, tol, peso, d });
        }
        if (sol.isEmpty()) return;

        // testa cada ângulo candidato: soma o peso de todos os inimigos que ele acertaria
        double melhorAng = sol.get(0)[0], melhorTol = sol.get(0)[1], melhorScore = -1;
        for (double[] c : sol) {
            double score = 0;
            for (double[] s : sol) {
                if (Math.abs(Utils.normalRelativeAngle(c[0] - s[0])) <= s[1]) score += s[2];
            }
            if (score > melhorScore) { melhorScore = score; melhorAng = c[0]; melhorTol = c[1]; }
        }

        setTurnGunRightRadians(Utils.normalRelativeAngle(melhorAng - getGunHeadingRadians()));
        if (getGunHeat() == 0 && atrasoAlvo <= 10 && distAlvo < 700
                && Math.abs(getGunTurnRemainingRadians()) < melhorTol) {
            setFire(potencia);
        }
    }

    double potenciaPara(Inimigo i, double distancia) {
        double potencia;
        if      (distancia < 150) potencia = 3.0;
        else if (distancia < 400) potencia = 2.0;
        else                      potencia = 1.2;
        double necessaria = (i.energia <= 4) ? i.energia / 4 : (i.energia + 2) / 6;
        potencia = Math.min(potencia, Math.max(necessaria, 0.1));
        if (getEnergy() < 20) potencia = Math.min(potencia, 1.0);
        return Math.min(potencia, getEnergy() - 0.1);
    }

    // previsão circular: onde o inimigo estará quando a bala chegar
    double[] preverCircular(Inimigo i, double velocidadeBala) {
        long atraso = getTime() - i.visto;
        double px = i.x, py = i.y, rumo = i.rumo;
        for (long k = 0; k < atraso; k++) {
            rumo += i.taxaGiro;
            px = limitar(px + Math.sin(rumo) * i.velocidade, getBattleFieldWidth());
            py = limitar(py + Math.cos(rumo) * i.velocidade, getBattleFieldHeight());
        }
        for (int t = 1; t < 200; t++) {
            if (t * velocidadeBala >= Point2D.distance(getX(), getY(), px, py)) break;
            rumo += i.taxaGiro;
            px = limitar(px + Math.sin(rumo) * i.velocidade, getBattleFieldWidth());
            py = limitar(py + Math.cos(rumo) * i.velocidade, getBattleFieldHeight());
        }
        return new double[] { px, py };
    }

    // *** Play It Forward *** //
    // histórico de movimento de cada inimigo (velocidade e giro por tick); 
    // static = guarda entre rounds
    static final int TAM_LOG = 6000;
    static final int PADRAO = 7;     // quantos ticks recentes comparamos
    static class Historico { double[] v = new double[TAM_LOG]; double[] g = new double[TAM_LOG]; int n = 0; }
    static Map<String, Historico> historicos = new HashMap<String, Historico>();

    void registrar(String nome, double v, double g) {
        Historico h = historicos.get(nome);
        if (h == null) { h = new Historico(); historicos.put(nome, h); }
        if (h.n >= TAM_LOG) { // descarta a metade mais antiga
            int meio = TAM_LOG / 2;
            System.arraycopy(h.v, meio, h.v, 0, TAM_LOG - meio);
            System.arraycopy(h.g, meio, h.g, 0, TAM_LOG - meio);
            h.n -= meio;
        }
        h.v[h.n] = v; h.g[h.n] = g; h.n++;
    }

    double[] prever(Inimigo i, double velocidadeBala) {
        double[] circ = preverCircular(i, velocidadeBala);
        Historico h = historicos.get(i.nome);
        if (h == null || h.n < 200 || getGunHeat() > 0.4) return circ; // só gasta CPU perto do tiro
        double dist = Point2D.distance(getX(), getY(), i.x, i.y);
        int ticksFuturo = (int) (dist / velocidadeBala) + 2 + (int) (getTime() - i.visto);
        int fim = h.n - 1;
        for (int m = 0; m < PADRAO; m++) if (Double.isNaN(h.v[fim - m])) return circ;

        // acha o trecho do passado mais parecido com os últimos PADRAO ticks
        int melhor = -1; double menor = Double.MAX_VALUE;
        int inicio = Math.max(PADRAO, h.n - 3000);
        for (int k = inicio; k < h.n - ticksFuturo - 1; k++) {
            double custo = 0;
            for (int m = 0; m < PADRAO && custo < menor; m++) {
                double dv = h.v[k - m] - h.v[fim - m], dg = (h.g[k - m] - h.g[fim - m]) * 40;
                custo += dv * dv + dg * dg;
            }
            if (Double.isNaN(custo) || custo >= menor) continue;
            menor = custo; melhor = k;
        }
        if (melhor < 0) return circ;

        // reproduz o que ele fez depois daquele trecho, a partir da posição atual
        double w = getBattleFieldWidth(), hh = getBattleFieldHeight();
        double px = i.x, py = i.y, rumo = i.rumo;
        long atraso = getTime() - i.visto;
        for (int t = 1; t < 200; t++) {
            int k = melhor + t;
            if (k >= h.n || Double.isNaN(h.v[k])) return circ;
            rumo += h.g[k];
            px += Math.sin(rumo) * h.v[k];
            py += Math.cos(rumo) * h.v[k];
            if (px < 18 || py < 18 || px > w - 18 || py > hh - 18) return circ; // atravessou parede: não confia
            if (t > atraso && (t - atraso) * velocidadeBala >= Point2D.distance(getX(), getY(), px, py)) break;
        }
        return new double[] { px, py };
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
        boolean muitosInimigos = getOthers() >= 2;

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
            repensar = true;
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
        // grava o movimento no histórico do PIF (preenche os ticks entre um scan e outro)
        if (dt == 0 || dt > 20) {
            registrar(e.getName(), Double.NaN, Double.NaN); // quebra: não dá pra emendar
        } else {
            for (long k = 1; k <= dt; k++) {
                double v = i.velocidade + (e.getVelocity() - i.velocidade) * k / dt;
                registrar(e.getName(), v, i.taxaGiro);
            }
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