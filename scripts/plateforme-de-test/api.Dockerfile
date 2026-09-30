# L'API Vigie-Chiro de la plateforme de test (#5661), à la révision que porte `epingles.properties`.
#
#   docker build -f scripts/plateforme-de-test/api.Dockerfile \
#       --build-arg REVISION=$(sed -n 's/^api.revision=//p' scripts/plateforme-de-test/epingles.properties) \
#       scripts/plateforme-de-test
#
# La révision n'a pas de valeur par défaut : une valeur ici serait une seconde épingle, et les deux
# divergeraient. Construite sans elle, l'image refuse.
#
# L'API vient de l'archive de sa révision, et non d'un `git clone` : ni `git` à installer, ni
# historique à rapatrier, et ce qui est installé est exactement l'arbre de ce commit.
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

WORKDIR /api

# `/api/v1` : le préfixe de la plateforme nationale, que Companion emploie. Sans lui, l'API servirait
# à la racine, et viser la plateforme de test demanderait une autre forme d'URL que la production.
# `DEV_FAKE_AUTH` frappe un jeton sans OAuth ; `DEV_FAKE_S3_URL` et `MONGO_HOST` se posent au
# démarrage, parce qu'ils dépendent de l'endroit où tournent le faux S3 et Mongo.
ENV BACKEND_URL_PREFIX=/api/v1 \
    BACKEND_PORT=8080 \
    DEV_FAKE_AUTH=true

EXPOSE 8080

# `runserver.py` fait `app.run()` sans hôte : Flask écouterait sur 127.0.0.1 DANS le conteneur, et le
# port publié ne mènerait à rien. Le rechargeur est laissé de côté : une API qui redémarre au milieu
# d'un test rend un verdict qu'on ne sait pas lire.
CMD ["python", "-c", "from vigiechiro import app, settings; app.run(host='0.0.0.0', port=settings.PORT)"]
