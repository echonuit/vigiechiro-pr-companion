#!/usr/bin/env python3
"""Le tag de la version que le train vient de produire, ou celui qu une reprise doit reprendre (#5987).

## Ce qu il empeche

L atelier de publication resolvait ce tag par `git describe --tags --exact-match HEAD`. Sur un train
normal cela suffit : semantic-release vient de pousser le commit de version, et `HEAD` le porte.

Sur une REPRISE, non. L exemple est mesure : le 2026-10-06, la 2.197.0 s est arretee sur la poussee
de sa note git, apres son commit et son tag. La reprise a consiste a creer le brouillon a la main puis
a relancer l atelier - et elle n a abouti que parce que la tete de `main` etait ENCORE le commit
taggue. Six minutes plus tard, `main` avait avance. Avec une fusion entre l echec et la relance,
`describe --exact-match HEAD` ne rend rien, la sortie du job est vide, et les jobs `installers` et
`publish` sont sautes par la garde qui leur exige un tag non vide - une seconde fois, en silence.

#4083 avait rencontre le meme mur sur la 2.186.0, avec 46 commits d ecart, et avait choisi de
supprimer le brouillon plutot que de relancer.

## Ce qu il fait, et ce qu il refuse de faire

Le premier moyen reste inchange : le tag porte par `HEAD`. Le repli ne sert qu a la reprise, et il est
BORNE, parce qu une borne mal choisie casse le train normal :

- est candidat un tag de version dont la VERSION est strictement superieure a la plus haute version
  PUBLIEE. Un tag sans publication qui est en dessous est une sequelle, pas une version en cours ;
- s il y a exactement un candidat, c est lui ;
- s il y en a PLUSIEURS, le script REFUSE en les nommant. Deux versions a moitie faites ne se
  departagent pas tout seules, et en choisir une au hasard publierait la mauvaise ;
- s il n y en a aucun, la sortie est vide, exactement comme aujourd hui.

## Pourquoi la version et non le graphe

Parce que le graphe mentirait. Mesure le 2026-10-06 : les 17 tags de version sans publication de ce
depot ne sont PAS des ancetres de `origin/main` - zero sur 17 - une reecriture d historique les ayant
orphelines. Une regle du genre « les tags qui ne sont pas derriere la derniere version publiee »
selectionnerait donc les 17, et ne bornerait rien. #4083 mesurait pourtant en aout une ancestralite
alors vraie : une mesure de graphe sur ces tags est DATEE, et une regle qui s y appuie rend un
resultat plausible et faux.

## Ce qu une forge muette ne doit pas devenir

Ce script demande a la forge quelles versions sont publiees. Une forge qui ne repond pas rend une
liste vide, et une liste vide de publications ferait croire que TOUTE version est « au-dessus de la
plus haute publiee ». Le repli proposerait alors n importe quoi.

La reponse vide a donc deux sens opposes selon l appelant, et c est le piege que #5544 a ferme pour
`releve-les-bancs-instables`, ou vide signifie « aucun banc instable » et doit PASSER. Ici vide
signifie « je n ai pas pu lire », et doit REFUSER. La moitie impure distingue les trois cas, et la
moitie pure recoit la liste des publiees comme un argument qui peut etre `None`.

Usage : python3 .github/scripts/resout_le_tag_de_version.py [--auto-test]
        Rend le tag sur la sortie standard, vide s il n y en a pas, et sort non zero sur un refus.
"""

from __future__ import annotations

import os
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile

MOTIF_DE_VERSION = re.compile(r"^v(\d+)\.(\d+)\.(\d+)$")


def version(tag: str) -> tuple[int, int, int] | None:
    """Le triplet d un tag de version, ou `None` si ce tag n en est pas un.

    Les tags qui ne suivent pas `vX.Y.Z` sont ecartes sans bruit : ce depot en porte d autres, et ils
    ne concernent pas la publication.
    """
    trouve = MOTIF_DE_VERSION.match(tag)
    return (int(trouve[1]), int(trouve[2]), int(trouve[3])) if trouve else None


class Refus(Exception):
    """Ce que le script ne peut pas trancher seul, et qui doit arreter le job."""


def resout(
    tag_de_la_tete: str,
    tags_de_version: list[str],
    versions_publiees: list[str] | None,
) -> str:
    """La moitie PURE : trois entrees, un tag ou un refus. Aucun git, aucune forge.

    @param tag_de_la_tete le tag porte par `HEAD`, chaine vide s il n y en a pas
    @param tags_de_version tous les tags `vX.Y.Z` du depot
    @param versions_publiees les tags publies, ou `None` quand la forge n a pas repondu
    """
    if tag_de_la_tete:
        return tag_de_la_tete

    if versions_publiees is None:
        raise Refus(
            "la forge n a pas repondu : impossible de savoir quelles versions sont publiees, donc"
            " impossible de distinguer une version a moitie faite d une sequelle. Ce n est pas un"
            " verdict, c est une lecture qui a echoue."
        )

    publiees = sorted(v for t in versions_publiees if (v := version(t)))
    plus_haute = publiees[-1] if publiees else (0, 0, 0)

    candidats = sorted((v, t) for t in tags_de_version if (v := version(t)) and v > plus_haute)
    if not candidats:
        return ""
    if len(candidats) > 1:
        noms = ", ".join(t for _, t in candidats)
        raise Refus(
            f"{len(candidats)} tags de version depassent la plus haute version publiee : {noms}."
            " Deux versions a moitie faites ne se departagent pas toutes seules, et en choisir une"
            " publierait peut-etre la mauvaise. Reprendre a la main, une version a la fois."
        )
    return candidats[0][1]


def tag_de_la_tete() -> str:
    """Le tag porte par `HEAD`, chaine vide s il n y en a pas. Un echec de git EST une absence ici."""
    rendu = subprocess.run(
        ["git", "describe", "--tags", "--exact-match", "HEAD"],
        capture_output=True,
        text=True,
        check=False,
    )
    return rendu.stdout.strip() if rendu.returncode == 0 else ""


def tags_de_version() -> list[str]:
    """Tous les tags `vX.Y.Z` du depot, par `git tag`."""
    rendu = subprocess.run(["git", "tag", "-l", "v*"], capture_output=True, text=True, check=False)
    if rendu.returncode != 0:
        raise Refus("`git tag` a refuse : impossible de lister les tags de version.")
    return [l.strip() for l in rendu.stdout.splitlines() if l.strip()]


def versions_publiees() -> list[str] | None:
    """Les tags que la forge declare publies, ou `None` si elle n a pas repondu.

    Les brouillons sont ECARTES : un brouillon est precisement l etat d une version a moitie faite,
    et le compter comme publie ferait taire le repli dans le cas ou il sert.

    Deux absences, deux conduites, et c est le garde de l ADR 5692 qui l a exigee ici : un `gh`
    introuvable fait REFUSER en nommant la panne d installation, parce que `subprocess.run` leverait un
    `FileNotFoundError` avant qu il y ait un code de sortie. Un `gh` present qui rend non zero, lui,
    rend `None` : la forge n a pas repondu, ce qui est une panne de lecture et non d installation.
    """
    if shutil.which("gh") is None:
        raise Refus(
            "« gh » est absent : ce script ne peut pas savoir quelles versions sont publiees, donc il"
            " ne peut pas distinguer une version a moitie faite d une sequelle. C est une panne"
            " d installation, et non un verdict sur la publication."
        )
    rendu = subprocess.run(
        ["gh", "release", "list", "--limit", "1000", "--json", "tagName,isDraft"],
        capture_output=True,
        text=True,
        check=False,
    )
    if rendu.returncode != 0:
        return None
    import json

    try:
        lu = json.loads(rendu.stdout or "[]")
    except json.JSONDecodeError:
        return None
    return [p["tagName"] for p in lu if not p.get("isDraft")]


def juger() -> int:
    """Rend le tag sur la sortie standard, et `1` avec le refus sur l erreur standard."""
    try:
        print(resout(tag_de_la_tete(), tags_de_version(), versions_publiees()))
    except Refus as refus:
        print(f"REFUS : {refus}", file=sys.stderr)
        return 1
    return 0


CONTRAT = {
    "garde": ".github/scripts/resout_le_tag_de_version.py",
    "geste": "resolution du tag de version que l atelier de publication consomme, reprise comprise",
    "population": "le tag de HEAD, les tags vX.Y.Z du depot, et les publications que la forge declare",
    "dispositif": "resolution",
    "seuil": "(sans objet)",
    "temoin": ".github/scripts/resout_le_tag_de_version.py --auto-test",
    "decision": "#5987",
    "chemins": """
.github/workflows/release.yml
.github/scripts/resout_le_tag_de_version.py
""",
}


def _auto_test() -> int:
    """Les cinq cas de la resolution, joues sur la moitie PURE : aucun git, aucune forge.

    Le cinquieme est celui que ce depot a fabrique tout seul, et sans lui les deux regles fausses que
    #5987 a essayees passeraient toutes les deux : dix-sept tags de version sans publication, tous en
    dessous de la plus haute publiee. Un repli qui les verrait refuserait a chaque train.
    """
    sequelles = [f"v2.{n}.0" for n in range(26, 36)] + ["v2.186.0"]

    cas: list[tuple[str, object]] = [
        (
            "train normal : le tag de HEAD gagne, sans meme regarder le reste",
            lambda: resout("v2.198.0", ["v2.197.0", "v2.198.0"], ["v2.197.0"]) == "v2.198.0",
        ),
        (
            "reprise : pas de tag sur HEAD, un seul tag au-dessus de la plus haute publiee",
            lambda: resout("", ["v2.196.0", "v2.197.0"], ["v2.196.0"]) == "v2.197.0",
        ),
        (
            "deux versions a moitie faites : refus qui les NOMME toutes les deux",
            lambda: _refuse_en_nommant(
                lambda: resout("", ["v2.196.0", "v2.197.0", "v2.198.0"], ["v2.196.0"]),
                ["v2.197.0", "v2.198.0"],
            ),
        ),
        (
            "rien a reprendre : la sortie est vide, comme avant ce script",
            lambda: resout("", ["v2.196.0"], ["v2.196.0"]) == "",
        ),
        (
            "les dix-sept sequelles sont ECARTEES : toutes sous la plus haute publiee",
            lambda: resout("", [*sequelles, "v2.197.0"], ["v2.197.0"]) == "",
        ),
        (
            "forge muette : REFUS, et non une liste vide prise pour une reponse",
            lambda: _refuse_en_nommant(
                lambda: resout("", ["v2.197.0"], None), ["la forge n a pas repondu"]
            ),
        ),
    ]

    cas.append(
        (
            "« gh » introuvable : REFUS qui nomme l outil, et non une trace de pile",
            lambda: _refuse_en_nommant(versions_publiees, ["« gh » est absent"], chemin_vide=True),
        )
    )

    echecs = []
    for libelle, joue in cas:
        try:
            tenu = bool(joue())
        except Exception as souci:  # noqa: BLE001 - un cas qui leve est un cas qui echoue
            tenu, libelle = False, f"{libelle} (a leve : {souci})"
        print(f"  {'✔' if tenu else '✘'} {libelle}")
        if not tenu:
            echecs.append(libelle)

    if echecs:
        print(f"\n❌ {len(echecs)} cas sur {len(cas)} ne tiennent pas.")
        return 1
    print(f"\n✅ {len(cas)} cas tenus : la resolution fait ce que son en-tete annonce.")
    return 0


def _refuse_en_nommant(joue, attendus: list[str], chemin_vide: bool = False) -> bool:
    """Vrai si l appel REFUSE et si son message porte chacun des elements attendus.

    Exiger le message et pas seulement le refus : un refus qui ne nomme pas ce qui le cause laisse
    chercher, et c est la moitie du service qu il rend.

    `chemin_vide` joue l appel sur un `PATH` fabrique et VIDE, ce qui est la seule facon d eprouver
    la branche « outil absent » sans desinstaller quoi que ce soit. Le garde de l ADR 5692 raconte
    pourquoi ce cas compte : un dispositif y promettait un refus dans sa docstring et levait avant de
    l atteindre, si bien que la promesse se citait sans etre tenue.
    """
    avant = os.environ.get("PATH", "")
    if chemin_vide:
        os.environ["PATH"] = str(pathlib.Path(tempfile.mkdtemp(prefix="sans-outils-")))
    try:
        joue()
    except Refus as refus:
        return all(attendu in str(refus) for attendu in attendus)
    finally:
        if chemin_vide:
            os.environ["PATH"] = avant
    return False


if __name__ == "__main__":
    if "--auto-test" in sys.argv:
        sys.exit(_auto_test())
    sys.exit(juger())
