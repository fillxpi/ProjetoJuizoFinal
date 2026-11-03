# \# JuizoFinal



Robô para o \[Robocode](https://robocode.sourceforge.io/), escrito em Java, desenvolvido em grupo para a competição entre turmas.



\## Integrantes



Alex da Silva de Souza

Daniella Lages Martins

Fillipi Augusto Lucio



\## Como rodar



1\. Instale o Robocode e abra o editor de robôs (Robot Editor).

2\. Crie a pasta/pacote `ProjetoJuizoFinal` (o nome da pasta precisa ser \*\*igual\*\* ao `package` da primeira linha do código, com as mesmas letras maiúsculas e minúsculas).

3\. Coloque o `JuizoFinal.java` nessa pasta e compile pelo próprio editor.

4\. Em \*\*Battle > New\*\*, selecione `ProjetoJuizoFinal.JuizoFinal` e os adversários, e inicie a batalha.



\## Como o robô funciona



O robô guarda os dados de cada inimigo que já viu (posição, energia, velocidade, direção e o momento do último scan) e, a cada tick, atualiza o radar, o movimento e o tiro.



\### Radar

\- Com \*\*mais de 3 inimigos vivos\*\*, o radar gira sem parar, vendo cada inimigo a cada 8 ticks.

\- Com \*\*3 ou menos\*\*, o radar acompanha o alvo escolhido, passando um pouco dele para não perdê-lo. A cada 24 ticks ele faz uma volta completa (por 9 ticks) para atualizar os outros inimigos.

\- Sem alvo, ou se o alvo some de vista por mais de 12 ticks, o radar volta a girar sem parar.



\### Escolha de alvo

Cada inimigo recebe uma pontuação (quanto menor, melhor):



```

pontuação = energia + distância \* 0.1

```



Assim, inimigos fracos e próximos têm prioridade. O robô só troca de alvo se o novo for pelo menos 15 pontos melhor que o atual, para o canhão não ficar indo de um inimigo para outro.



\### Mira

\- O canhão é independente do corpo e do radar, e é ajustado a cada tick.

\- A mira é \*\*circular\*\*: o robô mede quanto o inimigo gira por tick e simula o caminho dele até a bala chegar, em vez de supor que ele anda em linha reta.

\- A posição prevista é mantida dentro da arena.



\### Tiro

\- \*\*Potência por distância:\*\* 3,0 abaixo de 150 px, 2,0 abaixo de 400 px, 1,2 acima disso.

\- \*\*Não gasta mais do que o necessário\*\* para matar o alvo.

\- \*\*Com menos de 20 de energia\*\*, só tiros fracos (no máximo 1,0), e a energia nunca é gasta por inteiro.

\- O robô só atira quando o canhão está frio, alinhado ao alvo, com dados recentes (até 10 ticks) e o alvo está a menos de 700 px.



\### Movimento





\## Estrutura do código



| Parte | Onde |

|-------|------|

| Loop principal | `run()` |

| Radar | `controlarRadar()` |

| Mira e tiro | `mirarEAtirar()` |

| Escolha de alvo | `pontuacao()` e `escolherAlvo()` |

| Dados dos inimigos | classe `Inimigo` e `onScannedRobot()` |

| Eventos | `onRobotDeath()`, `onHitByBullet()`, `onHitWall()` |

