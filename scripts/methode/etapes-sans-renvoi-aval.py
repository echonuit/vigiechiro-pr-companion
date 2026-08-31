#!/usr/bin/env python3
"""Une etape numerotee ne delegue pas vers une competence situee plus loin dans le cycle (#4731).

Les six competences du cycle s enchainent dans un ordre :

    ouvrir-un-chantier -> ouvrir-une-issue -> ouvrir-une-pr
      -> clore-une-pr -> clore-une-issue -> clore-un-chantier

Une ETAPE qui delegue vers l aval envoie le lecteur faire, maintenant, un travail qui ne se fait
qu apres. Il ne peut pas obeir, et le texte ne lui dit pas pourquoi : il se croit en faute. C est
arrive le 2026-08-29, l etape 1 de `ouvrir-une-pr` deleguant a `clore-une-issue`, laquelle s emploie
apres la fusion depuis #4722 - et les deux se renvoyaient le corps de la demande de fusion.

**La PROSE est libre.** « Une fois fusionnee, `clore-une-issue` prend la suite » est juste et
necessaire : elle dit ou va le lecteur ensuite. Seules les etapes numerotees sont tenues, parce
qu elles prescrivent un geste a faire MAINTENANT.

**Les competences d appui ne sont pas dans le cycle.** `humaniser`, `trier-les-issues`,
`ecrire-une-adr` et les autres se convoquent a n importe quel moment ; elles ne sont pas ordonnees.

**Il REFUSE plutot que de sauter.** Les six ne titrent pas leur liste d etapes pareil - « Fonction
de garde », « Les cinq etapes », « Les quatorze passes ». Un motif cale sur un seul titre en sauterait
deux sur six et rendrait vert. La liste se DERIVE donc de sa forme, et une competence dont aucune
liste n est trouvee fait rougir : un garde qui ne sait pas lire le dit (article A3).

Usage :
    python3 scripts/methode/etapes-sans-renvoi-aval.py
    python3 scripts/methode/etapes-sans-renvoi-aval.py --auto-test
"""

import pathlib
import re
import sys

RACINE = pathlib.Path(__file__).resolve().parents[2]
FONDS = RACINE / ".agents" / "skills"

CYCLE = [
    "ouvrir-un-chantier",
    "ouvrir-une-issue",
    "ouvrir-une-pr",
    "clore-une-pr",
    "clore-une-issue",
    "clore-un-chantier",
]
RANG = {nom: i for i, nom in enumerate(CYCLE)}
NOMME = re.compile(r"\b((?:ouvrir|clore)-(?:un|une)-(?:chantier|issue|pr))\b")

ETAPE_LISTE = re.compile(r"^\s*(\d+)\.\s+\S")  # « 1. METTRE AU NET … »
ETAPE_TABLE = re.compile(r"^\|\s*(\d+)\s*\|")  # « | 0 | Trier … | »


def sections(texte: str) -> list[tuple[str, str]]:
    """Le document decoupe en (titre, corps), sur les titres de niveau deux."""
    parts = re.split(r"^## (.+)$", texte, flags=re.M)
    return list(zip(parts[1::2], parts[2::2]))


def etapes(texte: str) -> list[str] | None:
    """Les lignes d etapes de la PREMIERE section qui en porte au moins trois.

    Rend None quand aucune section n en porte : l appelant en fait un refus, jamais un silence.
    """
    for _titre, corps in sections(texte):
        lignes = [l for l in corps.split("\n") if ETAPE_LISTE.match(l) or ETAPE_TABLE.match(l)]
        if len(lignes) >= 3:
            return lignes
    return None


def ecarts() -> tuple[list[str], list[str]]:
    """Les delegations vers l aval, et les competences dont la liste n a pas ete trouvee."""
    fautes, illisibles = [], []
    for nom in CYCLE:
        f = FONDS / nom / "SKILL.md"
        if not f.exists():
            illisibles.append(f"{nom} : SKILL.md absent")
            continue
        pas = etapes(f.read_text(encoding="utf-8"))
        if pas is None:
            illisibles.append(f"{nom} : aucune liste d etapes reconnue")
            continue
        for ligne in pas:
            for cite in NOMME.findall(ligne):
                if cite == nom or cite not in RANG:
                    continue
                if RANG[cite] > RANG[nom]:
                    fautes.append(
                        f"{nom} delegue vers {cite} (plus loin dans le cycle)\n      {ligne.strip()}"
                    )
    return fautes, illisibles


def _auto_test() -> int:
    echecs = 0

    def verifie(libelle, obtenu, attendu):
        nonlocal echecs
        if obtenu == attendu:
            print(f"  ✔ {libelle}")
        else:
            print(f"  ✘ {libelle} : attendu {attendu!r}, obtenu {obtenu!r}")
            echecs = 1

    LISTE = "## Fonction de garde\n\n```\n1. FAIRE ceci\n2. FAIRE cela\n3. FAIRE encore\n```\n"
    verifie("une liste en bloc se lit", len(etapes(LISTE) or []), 3)

    TABLE = (
        "## Les quatre étapes\n\n| # | Étape |\n|---|---|\n"
        "| 0 | Trier |\n| 1 | Cartographier |\n| 2 | Planifier |\n"
    )
    verifie("une liste en table se lit aussi", len(etapes(TABLE) or []), 3)

    verifie(
        "une competence sans liste rend None, elle ne rend pas vide",
        etapes("## Contexte\n\nDeux phrases, aucune etape.\n"),
        None,
    )

    # Le sens NEGATIF : sans lui, un motif qui ne trouverait JAMAIS rien passerait les trois premiers.
    PROSE = "## Fonction de garde\n\n```\n1. FAIRE ceci\n```\n\nUne fois fusionnee, `clore-une-issue` prend la suite.\n"
    pas = etapes(PROSE)
    verifie("une prose qui nomme l aval n est pas une etape", pas, None)

    verifie("le rang du cycle ordonne bien", RANG["ouvrir-une-pr"] < RANG["clore-une-issue"], True)
    verifie("une competence d appui n est pas dans le cycle", "humaniser" in RANG, False)
    return echecs


if __name__ == "__main__":
    if "--auto-test" in sys.argv:
        raise SystemExit(_auto_test())
    fautes, illisibles = ecarts()
    for l in illisibles:
        print(f"ILLISIBLE : {l}", file=sys.stderr)
    for l in fautes:
        print(f"ÉCHEC : {l}", file=sys.stderr)
    if fautes or illisibles:
        print(
            "\nUne étape prescrit un geste à faire MAINTENANT. La déléguer à une compétence\n"
            "de l'aval envoie le lecteur faire ce qui ne se fait qu'après : il ne peut pas\n"
            "obéir, et se croit en faute. La prose, elle, reste libre de nommer la suite.",
            file=sys.stderr,
        )
        raise SystemExit(1)
    print(f"Les {len(CYCLE)} compétences du cycle : aucune étape ne délègue vers l'aval.")
    raise SystemExit(0)
