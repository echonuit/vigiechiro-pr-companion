"""Hook MkDocs : publie `CONSTITUTION.md` sur le site developpeur.

La constitution vit a la RACINE du depot, a cote de `CONTRIBUTING.md` : c'est un document de
gouvernance, et le descendre dans `dev-docs/` le rangerait parmi les pages qu'il gouverne. Mais le
site developpeur, lui, a besoin de le rendre : la doc y renvoie, et une regle qu'on ne peut pas
lire n'est pas opposable.

Le fichier est donc EXPOSE, pas copie. C'est le meme choix que `scripts/mkdocs_hooks.py` fait pour
les captures : une copie commitee dans `dev-docs/` serait un second exemplaire, et le jour ou les
deux divergeraient rien ne le dirait. La source unique reste le fichier de la racine.
"""

import os

from mkdocs.structure.files import File

SOURCE = "CONSTITUTION.md"
CIBLE = "constitution.md"


def on_files(files, config, **kwargs):
    """Ajoute la constitution au site, sous `constitution.md`."""
    racine = os.path.dirname(config["config_file_path"])
    chemin = os.path.join(racine, SOURCE)
    if not os.path.isfile(chemin):
        # Un hook muet sur l absence rendrait un site sans constitution, et la page de nav
        # deviendrait un 404 que le mode strict signale ailleurs. On le dit ici, ou on le sait.
        raise FileNotFoundError(f"{SOURCE} introuvable a la racine du depot : {chemin}")
    files.append(File.generated(config, CIBLE, abs_src_path=chemin))
    return files
