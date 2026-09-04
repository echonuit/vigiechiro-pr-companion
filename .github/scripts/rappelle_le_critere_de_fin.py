#!/usr/bin/env python3
"""Un lot ouvert sans critere de fin ne provoque aucun signal (#4977, porte du bash.)

## Ce qu il fait, et ce qu il ne fait PAS

`AGENTS.md` exige depuis #4975 que chaque lot dise dans son CORPS comment on saura qu il est fini.
Rien ne le tenait : le manque ne se decouvrait qu a la cloture du chantier, des semaines plus tard.

Ce script est une LOUPE, pas un garde. Il ne refuse rien, il rend un texte a poster en commentaire
sur le lot. Il ne peut faire rougir aucune demande de fusion, et c est delibere : les trois auditeurs
de l arbitrage de #4961 ont ecarte d une seule voix le rouge qui tombe sur qui n a pas la main, des
jours apres l ouverture qu il juge.

Il ne juge pas non plus la QUALITE d un critere. « Fini quand c est fait » lui convient. Deux dessins
de garde mecanique sur de la prose d EPIC ont deja ete mesures puis ecartes dans ce depot, faute de
signal lisible.

## Pourquoi il reconnait un lot de deux facons

La forge enregistre `parent_issue_added` dans le fil d une issue, mais **un workflow ne peut pas s y
abonner** : les types d activite de l evenement `issues` s arretent a `opened`, `edited`, `labeled` et
quinze autres, dont aucun ne concerne les sous-issues. Mesure le 2026-08-31.

Un lot est donc reconnu par son `parent`, lu a l execution, OU par la marque « Fait partie de #N » de
son corps, qui est la moitie versionnee du meme rattachement.

## Le cout d une erreur, et ce qu il autorise

**Signaler a tort coute un commentaire inutile ; se taire a tort ne coute rien de plus**, la loupe
hebdomadaire balayant le stock. C est ce qui separe cette loupe d un cliquet : un cliquet faux bloque,
celui-ci parle a cote ou se tait. Le motif peut donc rester genereux la ou un cliquet aurait du etre
exact.

## Le motif vit dans UN fichier

Deux dispositifs le lisent, celui-ci et `loupe-4992-lots-sans-critere.py`. Chacun portait le sien, et
les deux avaient diverge sur deux caracteres le jour de leur ecriture : la cinquieme formulation
manquait aux deux, et rien ne pouvait le dire (#4995, #4837). Voir `critere-de-fin.motif.md` pour les
contraintes de dialecte - le motif doit se lire a l identique par `grep -iE` sous `LC_ALL=C` et par
le module `re`.

Sorties :
  0, rien sur la sortie standard  : il n y a rien a dire
  0, un commentaire               : un rappel a poster, premiere ligne = MARQUE
  2                               : il n a pas pu lire, et ne conclut pas

Usage : python3 .github/scripts/rappelle_le_critere_de_fin.py <numéro d'issue>
        python3 .github/scripts/rappelle_le_critere_de_fin.py --auto-test
"""

from __future__ import annotations

import json
import os
import pathlib
import re
import shutil
import subprocess
import sys

RACINE = pathlib.Path(__file__).resolve().parents[2]

# La marque que le workflow cherche dans les commentaires existants avant de poster. Sans elle, un
# lot reedite trois fois recevrait trois fois le meme rappel.
MARQUE = "<!-- rappel-critere-de-fin -->"

# Le sas des suites : on y consigne, on n y prend rien. Une issue qui n y pend qu est pas un lot.
SAS = "4562"

# La marque de rattachement versionnee, celle que `ouvrir-une-issue` demande d ecrire dans le corps.
RATTACHEMENT = re.compile(r"Fait partie de #[0-9]+")

RAPPEL = f"""{MARQUE}
Ce lot ne dit pas **comment on saura qu'il est fini**, et `AGENTS.md` le demande dans le **corps** de
l'issue depuis #4975.

Un critère se vérifie. « Fini quand la loupe est écrite » ne dit rien de plus que le titre ; « fini
quand elle rougit sur un lot muet et se tait sur un lot qui dit son critère » se joue.

Le corps, et non un commentaire : un commentaire descend sous le fil, le corps est ce que la clôture
relit. Rien ne bloque, et cette loupe ne repassera pas."""


def critere() -> re.Pattern[str]:
    """Le motif partage, ou un REFUS : cette loupe ne conclut pas sans lui."""
    chemin = pathlib.Path(
        os.environ.get("CRITERE_MOTIF_FICHIER") or RACINE / "scripts/adr/critere-de-fin.motif"
    )
    if not chemin.is_file() or not os.access(chemin, os.R_OK):
        print(
            f"REFUS : « {chemin} » est illisible. Cette loupe ne conclut pas sans son motif.",
            file=sys.stderr,
        )
        raise SystemExit(2)
    return re.compile(chemin.read_text(encoding="utf-8").splitlines()[0], re.I)


def lit(numero: str, champ: str) -> str:
    """Le corps ou le parent d une issue. Injectables pour l auto-test."""
    injectee = os.environ.get("CRITERE_ISSUES_FICHIER")
    if injectee:
        corpus = json.loads(pathlib.Path(injectee).read_text(encoding="utf-8"))
        return str((corpus.get(numero) or {}).get(champ) or "")
    if shutil.which("gh") is None:
        print(
            "REFUS : « gh » est absent. Cette loupe ne conclut pas sur ce qu'elle n'a pas lu.",
            file=sys.stderr,
        )
        raise SystemExit(2)
    requete = {"corps": ("body", '.body // ""'), "parent": ("parent", ".parent.number // empty")}[
        champ
    ]
    rendu = subprocess.run(
        ["gh", "issue", "view", numero, "--json", requete[0], "-q", requete[1]],
        capture_output=True,
        text=True,
        check=False,
    )
    if rendu.returncode != 0:
        print(f"REFUS : la forge n'a pas répondu pour #{numero}.", file=sys.stderr)
        raise SystemExit(2)
    return rendu.stdout.strip()


def est_un_lot(corps: str, parent: str) -> bool:
    """Deux voies, et la seconde rattrape ce que l evenement de la forge ne dit pas."""
    if parent and parent != SAS:
        return True
    return not parent and bool(RATTACHEMENT.search(corps))


def juge(numero: str) -> int:
    """Le rappel sur la sortie standard, ou rien. Jamais un refus de fusion."""
    motif = critere()
    corps = lit(numero, "corps")
    parent = lit(numero, "parent")
    if not est_un_lot(corps, parent):
        return 0
    if motif.search(corps):
        return 0
    print(RAPPEL)
    return 0


CORPUS = {
    "1": {"corps": "Un lot sans rien.", "parent": "4961"},
    "2": {"corps": "Un lot.\n\n## Fini quand\n\nLe garde rougit.", "parent": "4961"},
    "3": {"corps": "Un lot.\n\n**Fait quand** : les six y sont.", "parent": "4961"},
    "4": {
        "corps": "Un lot.\n\n## Comment on saura que chaque lot est fini\n\nIl rougit.",
        "parent": "4961",
    },
    "5": {"corps": "Une trouvaille consignee.", "parent": "4562"},
    "6": {"corps": "Une issue libre, sans parent ni marque."},
    "7": {"corps": "Un lot pas encore rattache.\n\nFait partie de #4961"},
    "8": {
        "corps": "Un lot pas encore rattache, qui dit son critere.\n\nFini quand il rougit.\n\nFait partie de #4961"
    },
    "9": {"corps": "Un lot.\n\nOn ne saura pas s il est fini. Rien d autre.", "parent": "4961"},
    "10": {"corps": "Un lot.\n\n**Ce que je vérifierai** : le garde rougit.", "parent": "4961"},
}

# (attendu, libelle, numero). Le premier cas est celui qui compte : sans lui, tous les silences ne
# valent rien.
CAS = (
    ("rappel", "un lot muet reçoit un rappel", "1"),
    ("silence", "« Fini quand » en section suffit", "2"),
    ("silence", "« Fait quand » suffit aussi, c'est le mot de quatre EPIC", "3"),
    ("silence", "la section « Comment on saura... » suffit, c'est le mot de deux autres", "4"),
    ("silence", "une issue du sas n'est pas un lot : rien ne s'y prend", "5"),
    ("silence", "une issue sans parent ni marque n'est pas un lot", "6"),
    ("rappel", "la marque « Fait partie de » suffit à faire un lot, sans parent posé", "7"),
    ("silence", "un lot déclaré par la marque et qui dit son critère se tait", "8"),
    (
        "rappel",
        "« on ne saura pas s'il est fini » n'est pas un critère : la négation ne compte pas",
        "9",
    ),
    ("silence", "« Ce que je vérifierai » compte : c'est le mot que CLAUDE.md prescrit", "10"),
    # Un numero absent du corpus injecte : la loupe se tait, et c est le bon comportement - une issue
    # vide n est pas un lot. Le cas est ici pour que ce choix soit ecrit plutot que subi.
    ("silence", "un numéro inconnu du corpus se lit comme une issue sans rattachement", "999"),
)


def _auto_test() -> int:
    """Onze cas hors ligne, puis les deux refus, puis la marque d idempotence."""
    import contextlib
    import io
    import tempfile

    echecs = cas = signale = 0
    with tempfile.TemporaryDirectory(prefix="vc-critere-") as tmp:
        bac = pathlib.Path(tmp)
        (bac / "issues.json").write_text(json.dumps(CORPUS), encoding="utf-8")

        def joue(attendu: str, libelle: str, numero: str) -> str:
            nonlocal echecs, cas, signale
            cas += 1
            if attendu == "rappel":
                signale += 1
            os.environ["CRITERE_ISSUES_FICHIER"] = str(bac / "issues.json")
            tampon = io.StringIO()
            with contextlib.redirect_stdout(tampon), contextlib.redirect_stderr(io.StringIO()):
                try:
                    juge(numero)
                    code = 0
                except SystemExit as fin:
                    code = fin.code
            sortie = tampon.getvalue()
            obtenu = "rappel" if sortie.strip() else "silence"
            if code == 2:
                obtenu = "refus"
            if obtenu == attendu:
                print(f"  ✔ {libelle}")
            else:
                print(f"  ✘ {libelle} : attendu {attendu}, obtenu {obtenu}")
                echecs = 1
            return sortie

        for attendu, libelle, numero in CAS[:10]:
            joue(attendu, libelle, numero)

        # Le motif illisible fait REFUSER, pas conclure. Sans ce cas, retirer le refus laisse
        # l auto-test vert.
        cas += 1
        signale += 1
        os.environ["CRITERE_MOTIF_FICHIER"] = str(bac / "nulle-part.motif")
        tampon = io.StringIO()
        with contextlib.redirect_stdout(tampon), contextlib.redirect_stderr(tampon):
            try:
                juge("1")
                code = 0
            except SystemExit as fin:
                code = fin.code
        os.environ.pop("CRITERE_MOTIF_FICHIER", None)
        if code == 2 and "est illisible" in tampon.getvalue():
            print("  ✔ un motif illisible fait REFUSER au lieu de se taire")
        else:
            print(f"  ✘ un motif illisible fait REFUSER au lieu de se taire : code {code}")
            echecs = 1

        # La MARQUE doit etre dans le rappel, sinon le workflow reposterait a chaque edition.
        cas += 1
        sortie = joue("rappel", "le rappel porte sa marque d'idempotence en première ligne", "1")
        cas -= 1
        signale -= 1
        if not sortie.splitlines() or "rappel-critere-de-fin" not in sortie.splitlines()[0]:
            print("  ✘ le rappel porte sa marque d'idempotence en première ligne")
            echecs = 1

        # L APPEL, et non le verdict (ADR 4331). Les cas ci-dessus injectent tous un leurre et
        # n exercent jamais `lit` par son chemin reel. Celui-ci le lance avec `gh` hors du PATH.
        cas += 1
        signale += 1
        os.environ.pop("CRITERE_ISSUES_FICHIER", None)
        ancien_path = os.environ["PATH"]
        os.environ["PATH"] = str(bac / "sans-gh")
        tampon = io.StringIO()
        with contextlib.redirect_stdout(tampon), contextlib.redirect_stderr(tampon):
            try:
                juge("1")
                code = 0
            except SystemExit as fin:
                code = fin.code
        os.environ["PATH"] = ancien_path
        if code == 2 and "est absent" in tampon.getvalue():
            print("  ✔ sans « gh », l'appel REFUSE au lieu de se taire")
        else:
            print(f"  ✘ sans « gh », l'appel REFUSE au lieu de se taire : code {code}")
            echecs = 1

        joue(*CAS[10])

    os.environ.pop("CRITERE_ISSUES_FICHIER", None)
    print()
    print(f"{cas} cas, dont {signale} qui DOIVENT signaler ou refuser.")
    if echecs == 0:
        print(
            "Auto-test concluant : la loupe voit un lot muet, et se tait sur les formulations du motif."
        )
    else:
        print("Auto-test EN ÉCHEC.")
    return echecs


if __name__ == "__main__":
    if "--auto-test" in sys.argv[1:2]:
        sys.exit(_auto_test())
    if len(sys.argv) < 2:
        print(f"Usage : {sys.argv[0]} <numéro d'issue> | --auto-test", file=sys.stderr)
        sys.exit(2)
    sys.exit(juge(sys.argv[1]))
