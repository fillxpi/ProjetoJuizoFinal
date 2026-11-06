# JuizoFinal

Robô para o [Robocode](https://robocode.sourceforge.io/), escrito em Java, desenvolvido em grupo para a competição entre turmas.

## Integrantes

- Alex da Silva de Souza
- Daniella Lages Martins
- Fillipi Augusto Lucio

## Como rodar

1. Instale o Robocode e abra o editor de robôs (Robot Editor).
2. Crie a pasta/pacote `ProjetoJuizoFinal` (o nome da pasta precisa ser **igual** ao `package` da primeira linha do código, com as mesmas letras maiúsculas e minúsculas).
3. Coloque o `JuizoFinal.java` nessa pasta e compile pelo próprio editor.
4. Em **Battle > New**, selecione `ProjetoJuizoFinal.JuizoFinal` e os adversários, e inicie a batalha.

## Como o robô funciona

O robô guarda os dados de cada inimigo que já viu (posição, energia, velocidade, direção, giro e o momento do último scan) e, a cada tick, atualiza o radar, o movimento e o tiro. O corpo, o canhão e o radar giram de forma independente.

Algumas informações ficam guardadas em variáveis `static`, e por isso **continuam valendo entre os rounds** da mesma batalha: o histórico de movimento de cada inimigo, as estatísticas da mira estatística e a pontuação de cada mira.

### Radar

- Com **2 ou mais inimigos vivos**, o radar gira sem parar, vendo cada inimigo a cada 8 ticks.
- Com **1 inimigo vivo**, o radar acompanha o alvo, passando um pouco dele (cerca de 20°) para não perdê-lo.
- Sem alvo, ou se o alvo some de vista por mais de 12 ticks, o radar volta a girar sem parar.

### Escolha de alvo

Cada inimigo recebe uma pontuação (quanto menor, melhor):

```
pontuação = energia + distância * 0.1
```

Assim, inimigos fracos e próximos têm prioridade. O robô só troca de alvo se o novo for pelo menos 15 pontos melhor que o atual, para o canhão não ficar indo de um inimigo para outro.

### Tiro

- **Potência por distância:** 3,0 abaixo de 150 px, 2,0 abaixo de 400 px, 1,2 acima disso.
- **Não gasta mais do que o necessário** para matar o alvo.
- **Com menos de 20 de energia**, só tiros fracos (no máximo 1,0), e a energia nunca é gasta por inteiro.
- O robô só atira quando o canhão está frio, **alinhado** à direção calculada pela mira, com dados do alvo recentes (até 10 ticks) e o alvo a menos de 700 px.

### Mira

O robô tem **duas miras** e usa a que estiver acertando mais em cada inimigo.

**Mira 1: Play It Forward (PIF).** É a mira por padrão.
- O robô grava o histórico de movimento de cada inimigo (velocidade e giro a cada tick).
- Na hora de atirar, ele procura no histórico o trecho do passado mais parecido com os últimos 7 ticks do inimigo e **repete o que o inimigo fez depois daquele trecho**, a partir da posição atual dele.
- Se ainda não há histórico suficiente (menos de 200 ticks), ou se o caminho previsto bate na parede, ele usa a **previsão circular**: mede quanto o inimigo gira por tick e simula o caminho em curva até a bala chegar.
- Nesse modo, o robô testa o ângulo de cada inimigo visto recentemente e escolhe o que **tem mais chance de acertar alguém**, dando mais peso a quem está perto e uma preferência extra ao alvo escolhido.

**Mira 2: GuessFactor (GF).** É uma mira estatística.
- A cada scan, o robô solta uma "onda" a partir da sua posição. Quando a onda chega ao inimigo, ele registra para que lado e quanto o inimigo se moveu em relação à direção direta.
- Essas contagens são guardadas por inimigo e separadas por distância (5 faixas) e velocidade lateral (5 faixas). Na hora de atirar, o robô mira onde o inimigo mais costuma estar naquela situação.
- Nesse modo, a mira é sempre no alvo escolhido.

**Escolha entre as duas (virtual guns).** Enquanto o canhão está frio, o robô registra "balas virtuais" com os ângulos das duas miras. Quando a bala virtual chegaria ao inimigo, ele confere qual das duas teria acertado e atualiza uma pontuação, em que acertos antigos pesam menos. Para cada inimigo, passa a valer a mira de maior pontuação.

### Movimento

O movimento é contínuo e recalculado a cada tick, e muda conforme a quantidade de inimigos vivos.

**Com 3 ou mais inimigos: Minimum Risk Movement (movimento de menor risco).**
- O robô escolhe um destino sorteando pontos ao redor (a 100 a 220 px, sem chegar perto das paredes) e vai para o de menor risco.
- O risco de um ponto soma, para cada inimigo: a energia dele em relação à nossa, mais risco se o caminho é perpendicular a ele, mais risco se somos o robô mais próximo dele (provável alvo dos tiros), e o tudo dividido pela distância ao quadrado. Pontos perto de onde o robô acabou de estar também recebem uma penalidade.
- Ao chegar perto do destino (15 px), ou quando a esquiva dispara, ele sorteia 60 pontos novos. Nos outros ticks, só confere 8 pontos para ver se algum é bem melhor que o destino atual.

**Com 2 inimigos ou menos: anti-gravidade e esquiva.** O robô soma forças e anda na direção do resultado:
- **Repulsão dos inimigos:** cada um empurra o robô para longe (força proporcional a 1/d²).
- **Repulsão das paredes:** empurra o robô para dentro da arena.
- **Força lateral:** o robô circula o inimigo mais próximo. O lado da volta troca em intervalos aleatórios (entre 15 e 50 ticks).
- **Distância ideal:** uma força radial aproxima ou afasta o robô do inimigo mais próximo. A distância ideal é de **250 px** com 1 inimigo vivo e de **400 px** com 2.
- O inimigo mais próximo não repele o robô, e a distância dele é controlada só pela força radial.

**Em qualquer fase:**
- **Suavização de parede:** o robô olha 140 px à frente. Se esse ponto cair a menos de 40 px da parede, o caminho é girado em direção ao centro até ficar livre.
- **Marcha à ré:** se for mais rápido ir de ré do que girar, o robô anda de ré.
- **Esquiva de tiros:** se a energia de um inimigo cai entre 0,1 e 3,0, ele acabou de atirar. O robô inverte o lado da volta (que vale para a anti-gravidade) e manda o movimento de menor risco escolher outro destino. Existe um intervalo mínimo de 8 ticks entre duas esquivas.
- **Energia sem falso alarme:** quando o nosso tiro acerta, ou quando há uma colisão (que tira 0,6 de energia do outro robô), a energia do inimigo é atualizada na hora, para o robô não confundir isso com um tiro.

**Fuga de colisão.** Quando o robô bate em alguém, ele passa 14 ticks andando para longe, com um leve desvio lateral, ignorando o resto do movimento.

**Detector de travamento.** Se o robô fica quase parado por mais de 6 ticks seguidos, ele força a mesma fuga, para longe do inimigo mais próximo (ou para o centro, se não vê ninguém).

### Quando o robô é atingido

Se não tem alvo, passa a mirar em quem atirou.

## Estrutura do código

| Parte | Onde |
|-------|------|
| Loop principal | `run()` |
| Radar | `controlarRadar()` |
| Movimento (anti-gravidade e fuga) | `mover()`, `suavizarParede()`, `irPara()`, `distanciaIdeal()` e `maisProximo()` |
| Movimento de menor risco | `moverMRM()` e `risco()` |
| Mira e tiro | `mirarEAtirar()` e `potenciaPara()` |
| Mira 1 (PIF) | `prever()`, `preverCircular()` e `registrar()` |
| Mira 2 (GuessFactor) | `anguloGF()`, `atualizarOndas()` e `segmento()` |
| Escolha entre as miras | `registrarVirtual()`, `conferirVirtuais()` e `usaGF()` |
| Escolha de alvo | `pontuacao()` e `escolherAlvo()` |
| Dados dos inimigos | classe `Inimigo` e `onScannedRobot()` |
| Eventos | `onRobotDeath()`, `onHitByBullet()`, `onHitRobot()` e `onBulletHit()` |
