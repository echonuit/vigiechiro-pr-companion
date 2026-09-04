#!/usr/bin/env python3
"""Les parcours filmes de la doc existent, et chacun a son scenario au banc (porte du bash en #5229).

Tout parcours filme cite par une page de `docs/**/*.md` sous le motif `parcours-*.mp4` doit exister
dans `.github/assets/`, et tout film present doit correspondre a un parcours que le banc sait
tourner.

Pendant video de `check_doc_images.py`, avec une source de verite differente : les captures ont un
manifeste, les films n en ont pas besoin - la liste des parcours EST dans le banc, et c est elle
qu on interroge. Un film qui survivrait au renommage de son parcours serait sinon publie
indefiniment.

## Ce qu il ne verifie PAS, et il faut le savoir

Qu un parcours connu du banc ait son film. Tourner demande un serveur d affichage, openbox et une
carte montee ; l exiger de toute PR qui ajoute un scenario couterait plus qu il ne rapporte. Ce garde
attrape la reference morte et le film orphelin, pas la publication en retard.

## Pourquoi il LANCE le banc au lieu de le lire

Les noms candidats se relevent bien par un motif, mais un motif dit a quoi le banc RESSEMBLE, pas ce
qu il repond. Chaque candidat est donc soumis a `parcours_connu`, dans le banc lui-meme : c est la
regle d ADR 4331, un garde execute la regle qu il juge.

Le banc reste du shell, et ce garde le lance donc par `bash`. Quand la conversion l atteindra (lot 3
de #5215, celui des lourds), c est cet appel-la qui changera - et lui seul.

## Le mode `--site`, et pourquoi il existe a cote du mode par defaut

La premiere version verifiait que le film existait dans `.github/assets/`, et elle est restee VERTE
pendant que le site etait casse. MkDocs ne reecrit pas les chemins du HTML brut, seulement ceux du
Markdown : un `<source src="../assets/...">` reste litteral et pointe un cran trop haut. La page
rendait un lecteur video vide, sans une erreur. Verifier l existence du fichier source ne prouve donc
rien de ce qui compte ; ce mode ouvre les pages construites et suit chaque reference comme un
navigateur le ferait.

Usage : python3 .github/assets/check_doc_videos.py [--auto-test]
        python3 .github/assets/check_doc_videos.py --site [<dossier du site>]
        DOC_VIDEOS_RACINE=<dir> python3 .github/assets/check_doc_videos.py
"""

from __future__ import annotations

import os
import pathlib
import re
import subprocess
import sys

ICI = pathlib.Path(__file__).resolve().parent
RACINE = ICI.parent.parent
RACINE_INJECTEE = "DOC_VIDEOS_RACINE"

ASSETS = pathlib.Path(".github/assets")
BANC = pathlib.Path("scripts/doc-video/filme-un-parcours.sh")
FILM = re.compile(r"parcours-[a-z0-9-]+\.mp4")
NOM_DE_FILM = re.compile(r"^parcours-[a-z0-9-]+\.mp4$")
LIEN = re.compile(r'<(?:source|a)[^>]*(?:src|href)="([^"]*parcours-[a-z0-9-]+\.mp4)"')

# Le releve des candidats, puis la question posee au banc : identique a la version bash, et c est
# volontaire - la liste vient de ce que le banc REPOND, jamais de ce a quoi il ressemble.
INTERROGE_LE_BANC = r"""source "$0"
for nom in $(grep -oE "^        [a-z-]+\) printf" "$0" | tr -d " )" | sed "s/printf//"); do
    parcours_connu "$nom" >/dev/null 2>&1 && echo "$nom"
done"""


def racine_de(racine: pathlib.Path | None = None) -> pathlib.Path:
    return racine or pathlib.Path(os.environ.get(RACINE_INJECTEE, RACINE))


def parcours_connus(banc: pathlib.Path) -> list[str]:
    """Les parcours que le banc REPOND connaitre, tries et dedoublonnes."""
    rendu = subprocess.run(
        ["bash", "-c", INTERROGE_LE_BANC, str(banc)],
        capture_output=True,
        text=True,
        check=False,
        env={**os.environ, "BANC_SOURCE_SEULEMENT": "1"},
    )
    return sorted({l for l in rendu.stdout.splitlines() if l})


def juger(racine: pathlib.Path | None = None) -> int:
    """Le verdict du mode par defaut, et le code de sortie qui va avec."""
    base = racine_de(racine)
    banc = base / BANC
    if not banc.is_file():
        print(f"✗ {BANC} introuvable : la liste des parcours ne peut pas être lue.")
        return 1

    connus = parcours_connus(banc)
    if not connus:
        print(f"✗ aucun parcours lisible dans {BANC} : le garde ne peut rien affirmer.")
        return 1

    docs = base / "docs"
    referencees = set()
    if docs.is_dir():
        for page in docs.rglob("*.md"):
            referencees.update(FILM.findall(page.read_text(encoding="utf-8", errors="ignore")))
    dossier = base / ASSETS
    presentes = (
        {p.name for p in dossier.iterdir() if p.is_file() and NOM_DE_FILM.match(p.name)}
        if dossier.is_dir()
        else set()
    )

    erreurs = 0
    for film in sorted(referencees):
        if not (dossier / film).is_file():
            print(f"✗ {film} : référencé par la doc mais ABSENT de {ASSETS}/")
            erreurs += 1
    for film in sorted(presentes):
        nom = film[len("parcours-") : -len(".mp4")]
        if nom not in connus:
            print(
                f"✗ {film} : présent, mais « {nom} » n'est plus un parcours que le banc sait tourner"
            )
            erreurs += 1

    if erreurs == 0:
        if not referencees and not presentes:
            print("Aucun parcours filmé : rien à vérifier.")
        else:
            print("✓ Les parcours filmés référencés existent, et chacun a son scénario au banc.")
        return 0
    print()
    print(f"✗ {erreurs} parcours filmé(s) incohérent(s).")
    print(f"  Pour en ajouter un : un scénario dans {BANC}, puis")
    print(f"  bash {BANC} <nom> et le montage copié en {ASSETS}/parcours-<nom>.mp4.")
    return 1


def juger_le_site(site: str | pathlib.Path = "site") -> int:
    """Le mode `--site` : chaque reference est suivie comme un navigateur le ferait."""
    racine = pathlib.Path(site)
    if not racine.is_dir():
        print(f"✗ {site} : site construit introuvable ; rien ne peut être vérifié.")
        return 1

    erreurs = vues = 0
    for dossier, _, fichiers in os.walk(racine):
        for fichier in fichiers:
            if not fichier.endswith(".html"):
                continue
            page = pathlib.Path(dossier) / fichier
            for lien in LIEN.findall(page.read_text(encoding="utf-8", errors="ignore")):
                vues += 1
                cible = os.path.normpath(os.path.join(dossier, lien))
                if not os.path.isfile(cible):
                    print(
                        f"✗ {page.relative_to(racine)} : « {lien} » ne résout sur aucun fichier du site"
                    )
                    erreurs += 1
    if erreurs:
        print()
        print(f"✗ {erreurs} référence(s) de parcours cassée(s) dans le site construit.")
        print("  MkDocs ne réécrit PAS les chemins du HTML brut : depuis docs/ecrans/*.md,")
        print("  un parcours se référence en ../../assets/parcours/, comme les captures.")
        return 1
    if vues == 0:
        print("Aucune référence de parcours dans le site : rien à vérifier.")
    else:
        print(f"✓ Les {vues} référence(s) de parcours résolvent dans le site construit.")
    return 0


BANC_MINIMAL = """parcours_connu() {
    case "$1" in
        declarer-un-carre) printf '45\\tnon\\n' ;;
        importer-une-nuit) printf '120\\toui\\n' ;;
        *) return 1 ;;
    esac
}
"""


def _auto_test() -> int:
    """Les huit cas de la version bash : cinq sur le mode par defaut, trois sur `--site`."""
    import contextlib
    import io
    import tempfile

    echecs = cas = rouges = 0
    print("AUTO-TEST")

    def verifie(attendu: int, libelle: str, code: int) -> None:
        nonlocal echecs, cas, rouges
        cas += 1
        if attendu != 0:
            rouges += 1
        if code == attendu:
            print(f"  [OK   ] {libelle:<52} -> {code}")
        else:
            print(f"  [ÉCHEC] {libelle:<52} -> {code} (attendu {attendu})")
            echecs += 1

    def muet(action) -> int:
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            return action()

    with tempfile.TemporaryDirectory(prefix="vc-docvid-") as tmp:
        bac = pathlib.Path(tmp)
        (bac / ASSETS).mkdir(parents=True)
        (bac / "docs/ecrans").mkdir(parents=True)
        (bac / "scripts/doc-video").mkdir(parents=True)
        # Un banc minimal : ce garde n a besoin que de la liste des parcours.
        (bac / BANC).write_text(BANC_MINIMAL, encoding="utf-8")

        (bac / "docs/ecrans/vide.md").write_text("", encoding="utf-8")
        verifie(0, "aucun film référencé : rien à vérifier", muet(lambda: juger(bac)))

        (bac / "docs/ecrans/vide.md").write_text(
            "voir parcours-declarer-un-carre.mp4\n", encoding="utf-8"
        )
        verifie(1, "un film RÉFÉRENCÉ mais absent est refusé", muet(lambda: juger(bac)))

        (bac / ASSETS / "parcours-declarer-un-carre.mp4").touch()
        verifie(0, "un film référencé et présent est accepté", muet(lambda: juger(bac)))

        # Le cas qui porte ce garde : un film dont le parcours n existe plus.
        (bac / ASSETS / "parcours-ancien-nom.mp4").touch()
        verifie(1, "un film ORPHELIN de son parcours est refusé", muet(lambda: juger(bac)))
        (bac / ASSETS / "parcours-ancien-nom.mp4").unlink()

        # Sans banc lisible, on ne peut rien affirmer : il faut refuser, pas passer.
        (bac / BANC).rename(bac / "scripts/doc-video/absent")
        verifie(1, "sans le banc, le garde REFUSE au lieu de passer", muet(lambda: juger(bac)))
        (bac / "scripts/doc-video/absent").rename(bac / BANC)

        # --- le mode --site, celui qui suit les chemins comme un navigateur ---
        site = bac / "site"
        (site / "ecrans/sites").mkdir(parents=True)
        (site / "assets/parcours").mkdir(parents=True)
        (site / "assets/parcours/parcours-declarer-un-carre.mp4").touch()
        (site / "ecrans/sites/index.html").write_text(
            '<video><source src="../../assets/parcours/parcours-declarer-un-carre.mp4"></video>\n',
            encoding="utf-8",
        )
        verifie(0, "un chemin qui RÉSOUT est accepté", muet(lambda: juger_le_site(site)))
        # LE cas : c est ce chemin-la qui a ete livre, et le garde d alors le laissait passer.
        (site / "ecrans/sites/index.html").write_text(
            '<video><source src="../assets/parcours/parcours-declarer-un-carre.mp4"></video>\n',
            encoding="utf-8",
        )
        verifie(1, "un chemin d un cran trop haut est refusé", muet(lambda: juger_le_site(site)))
        import shutil

        shutil.rmtree(site)
        verifie(1, "sans site construit, le garde REFUSE", muet(lambda: juger_le_site(site)))

    print()
    print(f"{cas} cas, dont {rouges} qui DOIVENT rougir.")
    if echecs == 0:
        print("Auto-test concluant.")
        return 0
    print(f"AUTO-TEST EN ÉCHEC ({echecs}) : ne pas se fier au verdict de ce garde.")
    return 1


if __name__ == "__main__":
    if "--site" in sys.argv[1:2]:
        sys.exit(juger_le_site(sys.argv[2] if len(sys.argv) > 2 else "site"))
    if "--auto-test" in sys.argv:
        sys.exit(_auto_test())
    sys.exit(juger())
