# poc-chat
POC de chat pour le projet 13 d'OpenClassrooms.

## Prerequis
- Docker + Docker Compose
- Java 21 (utiliser le wrapper Maven fourni)
- Node.js + npm (Angular 15)

## Lancement en local (apres un `git pull`)
### 1) Docker (base de donnees MariaDB)
Depuis la racine du repo :

```powershell
docker compose -f docker/db/docker-compose.yml up -d
```

Pour arreter la base :

```powershell
docker compose -f docker/db/docker-compose.yml down
```

### 2) Backend (Spring Boot)
Dans `poc-chat/` :

```powershell
mvn spring-boot:run
```

Si Maven n'est pas installe, utilisez le wrapper :

```powershell
.\mvnw.cmd spring-boot:run
```

Le backend ecoute sur `http://localhost:8080`.

### 3) Frontend (Angular)
Dans `angular/` :

```powershell
npm install
ng serve
```

Le front est disponible sur `http://localhost:4200`.

## Configuration utile
- Base de donnees : `poc-chat/src/main/resources/application.properties`
- Front : `angular/src/environments/environment.ts`

Par defaut :
- API : `http://localhost:8080/api`
- WebSocket : `ws://localhost:8080/ws-chat`

## Deploiement (build)
### Backend
Dans `poc-chat/` :

```powershell
./mvnw -DskipTests package
```

Le jar est genere dans `poc-chat/target/` et peut etre lance ainsi :

```powershell
java -jar target/poc-chat-0.0.1-SNAPSHOT.jar
```

### Frontend
Dans `angular/` :

```powershell
npm run build
```

Le build se trouve dans `angular/dist/`. Le fichier `angular/src/environments/environment.prod.ts` utilise
des URLs relatives (`/api`, `/ws-chat`) : en production, il faut donc exposer le front et le backend sous
le meme domaine (ex: via un reverse proxy).

## Remarques
- Le schema et les donnees d'initialisation sont dans `docker/db/init.sql`.
- Le secret JWT par defaut est a changer dans `poc-chat/src/main/resources/application.properties`.
