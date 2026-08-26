# Image du back VigieChiro pour le banc d'étalonnage (ADR 4444).
#
# Le back n'est pas dans ce dépôt : il est cloné ici, à une révision ÉPINGLÉE. C'est voulu, et c'est
# le coeur du sujet. Toute la décision de l'ADR 4444 porte sur la dérive d'un back figé face à une
# plateforme qui bouge : un banc qui suivrait la branche amont ne saurait plus dire contre QUOI il a
# étalonné une sonde, et le verdict d'un tir cesserait d'être reproductible.
#
# Pour bouger l'épingle, on change REVISION et on le dit dans la PR : c'est une décision, pas une
# mise à jour.
FROM python:3.12-slim

ARG DEPOT=https://github.com/Scille/vigiechiro-api.git
ARG REVISION=5e924b1

RUN apt-get update \
    && apt-get install -y --no-install-recommends git \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /back
RUN git clone "$DEPOT" . && git checkout "$REVISION"

# Les épingles du back datent de 2021 (flask 1.1.4, werkzeug 0.16.1, pymongo 3.12) mais tournent sur
# Python 3.12 : l'amont a fait la migration, son `.python-version` et sa CI le disent tous les deux.
RUN pip install --no-cache-dir -r requirements.txt \
    && pip install --no-cache-dir -e .

EXPOSE 8080
CMD ["./runserver.py"]
