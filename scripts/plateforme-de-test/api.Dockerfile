# L'API Vigie-Chiro de la plateforme de test (#5661), et le front web qu'elle sert à sa racine (#6060),
# aux révisions que porte `epingles.properties`.
#
#   E=scripts/plateforme-de-test/epingles.properties
#   docker build -f scripts/plateforme-de-test/api.Dockerfile \
#       --build-arg REVISION=$(sed -n 's/^api.revision=//p' $E) \
#       --build-arg FRONT_REVISION=$(sed -n 's/^front.revision=//p' $E) \
#       scripts/plateforme-de-test
#
# Les révisions n'ont pas de valeur par défaut : une valeur ici serait une seconde épingle, et les deux
# divergeraient. Construite sans l'une d'elles, l'image refuse.
#
# L'API et le front viennent de l'archive de leur révision, et non d'un `git clone` : ni historique à
# rapatrier, et ce qui est installé est exactement l'arbre de ce commit.

# Les deux épingles se vérifient D'ABORD, avant toute construction. Le constructeur de Testcontainers
# construit les étapes dans l'ordre : sans cette étape, une image construite sans `REVISION` bâtissait
# tout le front avant de refuser, et `ImageDeLApiSansRevisionTest` disputait le runner à la plateforme
# (#6060). Chaque étape garde sa propre vérification : BuildKit saute une étape que rien ne référence.
FROM python:3.12-slim@sha256:f77ac9e44ae96ef2c90b8053ea08c31f8be030f824196b0ae4db6d462c84e51f AS epingles
ARG REVISION
ARG FRONT_REVISION
RUN test -n "$REVISION" \
    || { echo "REVISION est obligatoire : sa valeur est api.revision dans epingles.properties" >&2; exit 1; }
RUN test -n "$FRONT_REVISION" \
    || { echo "FRONT_REVISION est obligatoire : sa valeur est front.revision dans epingles.properties" >&2; exit 1; }

# Le front, construit comme son amont le déploie (`.github/workflows/ci.yml` de vigiechiro-front) :
# Node 22, `grunt-cli` et `bower`, puis `grunt build`. `git` est dans l'image : `bower` en a besoin.
FROM node:22-bookworm@sha256:0e5f906573693feaa1e21057ebdcfdb5bd5021f050b2dc7c9deceb629c7da2a8 AS front

ARG FRONT_REVISION
RUN test -n "$FRONT_REVISION" \
    || { echo "FRONT_REVISION est obligatoire : sa valeur est front.revision dans epingles.properties" >&2; exit 1; }

ADD https://github.com/Scille/vigiechiro-front/archive/${FRONT_REVISION}.tar.gz /tmp/front.tar.gz

# Les dépendances sont retirées dans la MÊME instruction, une fois `grunt build` fini. Le constructeur
# de Testcontainers garde l'image de cette étape, et chaque reconstruction en laissait une copie de
# plusieurs centaines de mégaoctets : trois mutations ont rempli le disque d'un poste (#6060).
#
# L'adresse de l'API est figée à la construction, dans `settings.coffee`. Elle est RELATIVE : le front
# est servi par l'API elle-même (`FRONTEND_HOSTED`, plus bas), donc sur la même origine, et le port
# publié, qu'on ne connaît qu'au démarrage, n'a pas à y figurer. Même origine, pas de CORS.
RUN mkdir /front \
    && tar -xzf /tmp/front.tar.gz -C /front --strip-components=1 \
    && rm /tmp/front.tar.gz \
    && cd /front \
    && npm install -g grunt-cli@1.3.2 bower@1.8.14 \
    && npm ci \
    && bower install --allow-root \
    && sed -i "s#API_DOMAIN.*#API_DOMAIN: '/api/v1'#" app/scripts/settings.coffee \
    && sed -i "s#FRONT_DOMAIN.*#FRONT_DOMAIN: ''#" app/scripts/settings.coffee \
    && grep -q "API_DOMAIN: '/api/v1'" app/scripts/settings.coffee \
    && grunt build \
    && rm -rf node_modules bower_components /root/.npm /root/.cache

# Ce que `index.html` charge de cdnjs, rapatrié à la construction et servi par l'API elle-même (#6061).
# Sans le sélecteur de dates, l'application ne démarre pas (mesuré : le retirer fait rougir la fiche ; la
# locale et Font Awesome, eux, ne font que manquer). Un test qui ouvre la fiche dépendait sinon d'Internet
# à chaque exécution, et le navigateur des tests coupe désormais tout hôte extérieur. Une adresse cdnjs restée dans la page fait échouer la construction.
RUN CDNJS=https://cdnjs.cloudflare.com/ajax/libs \
    && cd /front/dist \
    && for f in \
        angular-bootstrap-datetimepicker/1.1.2/css/datetimepicker.min.css \
        angular-bootstrap-datetimepicker/1.1.2/js/datetimepicker.min.js \
        angular-bootstrap-datetimepicker/1.1.2/js/datetimepicker.templates.min.js \
        angular-i18n/1.2.15/angular-locale_fr-fr.js \
        font-awesome/4.6.3/css/font-awesome.min.css \
        font-awesome/4.6.3/fonts/fontawesome-webfont.eot \
        font-awesome/4.6.3/fonts/fontawesome-webfont.woff2 \
        font-awesome/4.6.3/fonts/fontawesome-webfont.woff \
        font-awesome/4.6.3/fonts/fontawesome-webfont.ttf \
        font-awesome/4.6.3/fonts/fontawesome-webfont.svg; \
       do curl -fsSL --create-dirs -o "cdnjs/$f" "$CDNJS/$f" || exit 1; done \
    && sed -i "s#https://cdnjs.cloudflare.com/ajax/libs/#cdnjs/#g" index.html \
    && ! grep -q "cdnjs.cloudflare.com" index.html

FROM python:3.12-slim@sha256:f77ac9e44ae96ef2c90b8053ea08c31f8be030f824196b0ae4db6d462c84e51f

ARG REVISION
RUN test -n "$REVISION" \
    || { echo "REVISION est obligatoire : sa valeur est api.revision dans epingles.properties" >&2; exit 1; }

LABEL fr.echonuit.vigiechiro-api.revision=$REVISION

ADD https://github.com/Scille/vigiechiro-api/archive/${REVISION}.tar.gz /tmp/api.tar.gz

# Les dépendances de l'API datent de 2021 (flask 1.1.4, werkzeug 0.16.1, pymongo 3.12) mais tournent
# sur Python 3.12 : l'amont a fait la migration, son `.python-version` et sa CI le disent.
RUN mkdir /api \
    && tar -xzf /tmp/api.tar.gz -C /api --strip-components=1 \
    && rm /tmp/api.tar.gz \
    && pip install --no-cache-dir -r /api/requirements.txt \
    && pip install --no-cache-dir -e /api

# `unzip` : le serveur extrait une archive déposée en appelant ce binaire, et non la bibliothèque de
# Python (`TASK_PARTICIPATION_EXTRACT_BACKEND`, `unzip` par défaut). `joue_l_extraction.py` lance cette
# moitié du worker pour observer ce qu'une archive du repli devient (#5970). Posé après les dépendances,
# pour ne pas refaire leur couche.
RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /api

# Le front construit plus haut, là où `FRONTEND_HOSTED` le cherche : `vigiechiro/app.py` sert à la
# racine le dossier `static` de `/api`. Posé après les dépendances et `unzip`, pour ne pas refaire
# leurs couches quand seul le front change.
COPY --from=front /front/dist /api/static

# `/api/v1` : le préfixe de la plateforme nationale, que Companion emploie. Sans lui, l'API servirait
# à la racine, et viser la plateforme de test demanderait une autre forme d'URL que la production.
# `FRONTEND_HOSTED` sert le front à la racine, à côté de l'API (#6060).
# `DEV_FAKE_AUTH` frappe un jeton sans OAuth ; `DEV_FAKE_S3_URL` et `MONGO_HOST` se posent au
# démarrage, parce qu'ils dépendent de l'endroit où tournent le faux S3 et Mongo.
ENV BACKEND_URL_PREFIX=/api/v1 \
    BACKEND_PORT=8080 \
    DEV_FAKE_AUTH=true \
    FRONTEND_HOSTED=true

EXPOSE 8080

# L'API est lancée par `gunicorn`, comme l'amont la lance en production (son `Procfile`), et non par
# le serveur de développement de Flask (#6183). Celui-ci laissait par intermittence une écriture sans
# réponse : la requête manquait au journal de l'API, qui n'avait ni redémarré ni planté, et la JVM
# lisait « header parser received no bytes ». Les workers synchrones ne gardent aucune connexion
# ouverte, donc aucune ne peut être réutilisée après que le serveur l'a fermée. Quatre, pour qu'un
# navigateur qui charge la fiche et une écriture ne s'attendent pas. Le journal d'accès va sur la
# sortie : le journal de l'API garde une ligne par requête (`JournauxDeLaPlateformeDeTestTest`).
CMD ["gunicorn", "vigiechiro:app", "--bind", "0.0.0.0:8080", "--workers", "4", "--access-logfile", "-", "--log-file", "-"]
