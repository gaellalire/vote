
# Use Postgres
docker run -p5432:5432 --name vote-postgres -e POSTGRES_PASSWORD=mysecretpassword -d postgres

docker exec -it vote-postgres psql -U postgres

create database "stateDB";