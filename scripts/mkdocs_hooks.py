"""Hook MkDocs : expose les captures d'écran et les parcours filmés dans le site.

Les aperçus `.github/assets/apercu-*.png` sont **régénérés depuis le code** par le workflow
« Aperçus des vues » (`.github/workflows/capture-vues.yml`). Ce hook les rend disponibles dans le
site sous `assets/captures/<nom>.png`, **sans en committer de copie** dans `docs/` : la source
unique reste le code. Une page qui change d'écran reste ainsi illustrée par la version courante.

Référencer une capture depuis une page (chemin relatif vers la racine du site) :
- depuis `docs/index.md`            : `assets/captures/apercu-accueil.png`
- depuis `docs/ecrans/un-ecran.md`  : `../assets/captures/apercu-accueil.png`

Le garde-fou `.github/assets/check-doc-images.sh` vérifie en CI que toute capture référencée par
une page existe et est déclarée dans `captures.manifest`.

Les parcours filmés `.github/assets/parcours-*.mp4` suivent le même chemin, sous
`assets/parcours/<nom>.mp4`. Ils sont produits par `scripts/doc-video/filme-un-parcours.sh`, qui
pilote le fat-jar avec `xdotool` et vérifie le libellé visé avant chaque clic ; leur garde-fou est
`.github/assets/check-doc-videos.sh`.

⚠️ Le montage est en MP4 là où le tournage est en MKV : aucun navigateur n'affiche le Matroska, et
le MKV ne protège que le tournage (un `ffmpeg` tué laisse un MP4 sans index).
"""

import os

from mkdocs.structure.files import File


# Ce que le hook expose, et sous quel dossier du site.
EXPOSES = (
    ("apercu-", ".png", "assets/captures"),
    ("parcours-", ".mp4", "assets/parcours"),
)


def on_files(files, config, **kwargs):
    """Ajoute chaque capture et chaque parcours filmé au site."""
    racine = os.path.dirname(config["config_file_path"])
    source = os.path.join(racine, ".github", "assets")
    if not os.path.isdir(source):
        return files
    for nom in sorted(os.listdir(source)):
        for prefixe, extension, dossier in EXPOSES:
            if nom.startswith(prefixe) and nom.endswith(extension):
                files.append(
                    File.generated(
                        config,
                        f"{dossier}/{nom}",
                        abs_src_path=os.path.join(source, nom),
                    )
                )
    return files
