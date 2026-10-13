"""Le reglage de `gunicorn` pour l API de la plateforme de test (#6183).

L API est servie comme l amont la sert en production, par `gunicorn` et non par le serveur de
developpement de Flask. Ses workers synchrones ne gardent aucune connexion ouverte.

En se chargeant, l API appelle `logging.config.dictConfig` sans garder les journaux existants : le
journal d acces de `gunicorn`, cree avant elle, en sort desactive, et le journal de l API ne portait
plus aucune requete. `post_worker_init` le rouvre une fois l API chargee dans chaque worker.
"""

import logging

bind = "0.0.0.0:8080"
# Quatre, pour qu un navigateur qui charge la fiche et une ecriture ne s attendent pas.
workers = 4
accesslog = "-"
errorlog = "-"


def post_worker_init(worker):
    for nom in ("gunicorn.access", "gunicorn.error"):
        logging.getLogger(nom).disabled = False
