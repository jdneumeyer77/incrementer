FROM postgres
ENV POSTGRES_PASSWORD password123
ENV POSTGRES_USER thecounter
COPY migrations/init.sql /docker-entrypoint-initdb.d/