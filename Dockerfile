FROM clojure:lein

WORKDIR /app

# Copiar o projeto completo
COPY . /app/

# Mostrar estrutura de diretórios para depuração
RUN ls -la
RUN ls -la src

# Instalar dependências
RUN lein deps

# Compilar o projeto com mais informações de depuração
RUN lein uberjar
RUN ls -la target/

# Expor a porta 8080
EXPOSE 8080

# Definir a variável de ambiente para a chave da API (pode ser sobrescrita no runtime)
ENV YOUTUBE_API_KEY="YOUTUBE_API_KEY"

# Executar o aplicativo - usando o caminho correto para o JAR
CMD ["java", "-jar", "target/uberjar/youtube-comments-0.1.0-SNAPSHOT-standalone.jar"]
