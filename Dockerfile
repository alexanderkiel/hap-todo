FROM gfredericks/leiningen:java-8-lein-2.5.1

COPY resources /app/resources
COPY src /app/src
COPY project.clj /app/

WORKDIR /app

RUN lein with-profile production deps

EXPOSE 80
ENV PORT 80

CMD ["lein", "with-profile", "production", "trampoline", "run"]
