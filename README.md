# AnaRapport

Visualizador de rapport (repetição de padrão têxtil) em Java Swing. Carrega uma
imagem e a exibe repetida em grade, com zoom, pan e três modos de repetição.

## Requisitos

- Java 17+
- Maven 3.8+

## Como rodar

```bash
mvn clean package
java -jar target/anarapport.jar
```

O plugin `maven-shade-plugin` já empacota um jar executável autocontido em
`target/anarapport.jar` (com o manifest apontando para `com.anarapport.app.Main`).

## Uso

- **Arquivo → Abrir imagem**: carrega um PNG ou JPEG como motivo do rapport.
- **Toolbar de modo**: alterna entre os três modos de repetição a qualquer
  momento, mantendo o zoom/pan atuais.
- **Tamanho da grade**: controla quantas colunas do motivo cabem na largura
  visível (3 a 9).
- **Mostrar linhas de emenda**: desenha uma linha sutil na borda de cada
  célula, com estilo selecionável (cinza escuro, branco, tracejada cinza ou
  vermelho de alto contraste), para localizar visualmente os pontos de emenda.
- **Zoom**: roda do mouse, centralizado no cursor.
- **Pan**: arrastar com o botão esquerdo pressionado.
- **Arquivo → Exportar composição**: renderiza exatamente o que está visível
  na tela (grade, modo de rapport e linhas de emenda, se ativadas) para um
  PNG ou JPEG.

## Modos de rapport

- **Reto**: o motivo é repetido em uma grade simples, todas as linhas e
  colunas alinhadas, sem nenhum deslocamento entre elas.
- **Half Drop**: colunas alternadas são deslocadas verticalmente em metade da
  altura do motivo, seguindo a convenção têxtil de meio-rapport.
- **Espelhado**: cada célula alterna espelhamento horizontal, vertical ou nos
  dois eixos conforme a paridade de linha/coluna (um padrão xadrez de
  orientações), de forma que o motivo se reflete continuamente em toda borda
  compartilhada entre células vizinhas.

## Estrutura do código

- `app` — ponto de entrada (`Main`) e montagem da interface.
- `ui` — `ImagePanel`, o painel customizado que desenha a grade e trata
  zoom/pan.
- `render` — `RapportRenderer`, responsável pelo algoritmo de tiling de cada
  modo de rapport (inclui um cache de tiles pré-escalados para manter o pan/
  zoom fluido).
- `model` — estado da aplicação (`AppState`) e os enums `RapportType` e
  `SeamStyle`.
- `io` — carregamento (`ImageLoader`) e exportação (`ImageExporter`) de
  imagens em disco.
