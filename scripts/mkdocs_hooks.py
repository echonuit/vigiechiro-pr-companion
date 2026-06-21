"""Hook MkDocs : expose les captures d'écran de l'application dans le site.

Les aperçus `.github/assets/apercu-*.png` sont **régénérés depuis le code** par le workflow
« Aperçus des vues » (`.github/workflows/capture-vues.yml`). Ce hook les rend disponibles dans le
site sous `assets/captures/<nom>.png`, **sans en committer de copie** dans `docs/` : la source
unique reste le code. Une page qui change d'écran reste ainsi illustrée par la version courante.

Référencer une capture depuis une page (chemin relatif vers la racine du site) :
- depuis `docs/index.md`            : `assets/captures/apercu-accueil.png`
- depuis `docs/ecrans/un-ecran.md`  : `../assets/captures/apercu-accueil.png`

Le garde-fou `.github/assets/check-doc-images.sh` vérifie en CI que toute capture référencée par
une page existe et est déclarée dans `captures.manifest`.
"""

import os

from mkdocs.structure.files import File


def on_files(files, config, **kwargs):
    """Ajoute chaque `apercu-*.png` au site, sous `assets/captures/`."""
    racine = os.path.dirname(config["config_file_path"])
    source = os.path.join(racine, ".github", "assets")
    if not os.path.isdir(source):
        return files
    for nom in sorted(os.listdir(source)):
        if nom.startswith("apercu-") and nom.endswith(".png"):
            files.append(
                File.generated(
                    config,
                    f"assets/captures/{nom}",
                    abs_src_path=os.path.join(source, nom),
                )
            )
    return files
