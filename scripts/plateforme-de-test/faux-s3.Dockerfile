# Le faux stockage d'objets de la plateforme de test (#5660). Voir `faux_s3.py` pour ce qu'il fait
# et pourquoi celui de l'API amont ne peut pas servir.
#
#   docker build -f scripts/plateforme-de-test/faux-s3.Dockerfile scripts/plateforme-de-test
#
# La base est épinglée par digest : une étiquette bouge sans prévenir, et une plateforme de test qui
# change sous les tests ne dit plus contre quoi ils ont été joués. `openssl` y est déjà, si bien que
# la construction ne demande aucun réseau au-delà de la base.
FROM python:3.12-slim@sha256:f77ac9e44ae96ef2c90b8053ea08c31f8be030f824196b0ae4db6d462c84e51f

COPY faux_s3.py /faux-s3/faux_s3.py

# Les noms que le certificat couvre. Companion refuse une URL de dépôt qui n'est pas en https, et la
# JVM refuse un certificat qui ne couvre pas l'hôte joint : l'extension de #5663 fixe cette liste à
# l'hôte que Testcontainers lui donne.
ENV FAUX_S3_NOMS="DNS:localhost,IP:127.0.0.1"

EXPOSE 8443

# Le certificat naît au démarrage, jamais à la construction : une clé privée figée dans une couche
# d'image serait la même pour tous, et survivrait au conteneur. L'extension le lit ensuite par
# `/certificats/faux-s3.pem` pour le faire accepter par la JVM des tests.
CMD ["sh", "-c", "mkdir -p /certificats && openssl req -x509 -newkey rsa:2048 -nodes -days 2 -subj /CN=faux-s3 -addext \"subjectAltName=$FAUX_S3_NOMS\" -keyout /certificats/faux-s3.key -out /certificats/faux-s3.pem 2>/dev/null && exec python3 /faux-s3/faux_s3.py --port 8443 --certificat /certificats/faux-s3.pem --cle /certificats/faux-s3.key"]
