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
- **Offset horizontal / vertical (%)**: afasta ou sobrepõe as células, em
  percentual do tamanho do motivo (-50% a 100%, padrão 0% = encaixe perfeito).
  Valores positivos abrem um gap visível entre réplicas; negativos simulam
  leve sobreposição. Vale para os três modos de rapport.
- **Zoom**: roda do mouse, centralizado no cursor.
- **Pan**: arrastar com o botão esquerdo pressionado.
- **Arquivo → Exportar composição**: renderiza exatamente o que está visível
  na tela (grade, modo de rapport e linhas de emenda, se ativadas) para um
  PNG ou JPEG.
- **Imagem → Informações da imagem...**: abre um diálogo com dados técnicos
  do arquivo voltados para quem for retrabalhá-lo em outra ferramenta (ex.:
  Photoshop) — dimensões em pixels, DPI (quando o arquivo
  informa), tamanho físico estimado em cm/polegadas, modo de cor, profundidade
  de bit, perfil ICC embutido, formato, tamanho em disco, caminho, data de
  modificação e metadados EXIF (câmera, data de captura, orientação), quando
  presentes. Campos não disponíveis no arquivo aparecem como "Não informado"
  em vez de serem omitidos. A seção **Análise avançada**, ao final do
  diálogo, só roda sob demanda (botão "Analisar imagem", com barra de
  progresso via `SwingWorker`, já que envolve processar a imagem pixel a
  pixel): contagem de cores únicas, presença/percentual de transparência,
  aviso aproximado de cores fora do gamut CMYK, checagem de resolução para um
  tamanho de impressão informado (cm ou polegadas, com DPI mínimo
  configurável), qualidade estimada quando o arquivo é JPEG, paleta de cores
  dominantes (com amostra visual e valor hex/RGB) e um score de continuidade
  de borda (compara os pixels da borda esquerda/direita e superior/inferior,
  simulando como ficariam ao repetir a imagem lado a lado).

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
- `model` — estado da aplicação (`AppState`), os enums `RapportType` e
  `SeamStyle`, e os registros `ImageMetadata`/`ExifInfo`.
- `io` — carregamento (`ImageLoader`), exportação (`ImageExporter`) e leitura
  de metadados técnicos (`ImageMetadataReader`, `ExifReader`) de imagens em
  disco.
- `analysis` — `ImageAnalyzer`, os cálculos pixel a pixel da "Análise
  avançada" (cores únicas/dominantes, transparência, gamut CMYK aproximado,
  continuidade de borda, checagem de resolução, qualidade JPEG estimada),
  reutilizados pela UI apenas para disparar a análise e exibir o resultado.
