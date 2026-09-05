#!/usr/bin/env python3
"""Un chantier clos sans repondre a la passe de specification (#4922, porte du bash).

Il ne dit alors pas si le produit a ete specifie ou si la question ne s est pas posee.

## Ce que ce garde mesure, et ce qu il ne mesure pas

La passe 10 du cycle demande d archiver le changement OpenSpec, ou de dire pourquoi il n y en avait
pas. Ce garde verifie que la trace de cloture porte cette ligne, cochee. C est une convention, pas
une preuve : une cloture peut la cocher a tort. Il ne mesure donc pas si la specification est JUSTE,
il mesure qu un jugement a ete rendu la ou le cycle le demande.

## Pourquoi un cliquet de deficit, et pas une fraction de couverture

La question « combien de capacites sur combien » n a pas de denominateur : le depot en offre cinq
incompatibles (16 ecrans, 74 commandes, 416 cas de recette, 35 EPIC, 27 services de domaine), et
choisir entre eux revient a decider ce qu est une capacite du produit. Un deficit se compte sans
denominateur, exactement comme celui d ADR 4659 dont ce garde est le voisin et la copie.

## Le corpus s arrete a la naissance de la passe

La passe 10 est entree dans le cycle par #4840, le 2026-08-30 a 09:00:15Z. Un EPIC clos avant ne
pouvait pas y repondre. Cette borne est un fait historique : elle ne se met pas a jour.

## Le motif, et les deux raisons vecues qui l ont forme

Il exige DEUX mots sur la MEME ligne, « OpenSpec » et « archiv », dans l un ou l autre ordre. Le
modele du cycle ecrit « Changement OpenSpec archive », les clotures reelles ecrivent « Archivage
OpenSpec » : exiger la formulation du modele faisait compter #4882 fautive alors qu elle avait
repondu. Mais « OpenSpec » seul ne suffit pas non plus, et cet en-tete a affirme le contraire pendant
une demi-journee - la trace de #4882 le dementait le jour meme, a la passe 6, « sept gardes de
methode verts, dont les deux d OpenSpec » (#4938).

La ligne se reconnait par son CONTENU et non par son numero : #4840 a renumerote le cycle, et dans
une trace anterieure « 10. » designe la passe des ADR. Un garde qui matcherait le numero compterait
douze clotures comme specifiees alors qu aucune ne l etait.

**Ce qui reste heuristique, et se declare.** Deux mots sur une ligne ne sont pas une reconnaissance
exacte : une passe 6 qui parlerait des « gardes d archivage OpenSpec » tromperait encore ce motif.
C est l article A3, et c est la raison du niveau `probable`.

Usage : python3 .github/scripts/verifie_specification_consignee.py [--auto-test]
"""

from __future__ import annotations

import json
import os
import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from _forge import (
    cas_d_auto_test_de_forge,
    cliquet_declare,
    liste_issues,
    racine,
    vue_issue,
)

# L en-tete du modele de `dev-docs/cycle-de-chantier.md`, comme chez ADR 4659.
MARQUE = "## Clôture de chantier"

# L ancrage est « (^|\n) » et non « ^ » : le motif vient du dialecte de jq, ou « ^ » ne vaut qu au
# debut de la CHAINE. Il se lit a l identique par le module `re` sans le drapeau multiligne.
LIGNE = re.compile(
    r"(^|\n)- \[x\] [0-9]+[a-z]?\.[^\n]*(OpenSpec[^\n]*[Aa]rchiv|[Aa]rchiv[^\n]*OpenSpec)"
)

# Le commit qui a cree la passe 10 (#4840). Fait historique, jamais mis a jour.
DEPUIS = "2026-08-30T09:00:15Z"

ADR = "dev-docs/decisions/4922-l-adoption-d-une-specification-se-tient-par-un-cliquet.md"


def cliquet() -> int:
    return cliquet_declare(pathlib.Path(os.environ.get("SPEC_ADR_FICHIER") or racine() / ADR))


def epics() -> list[dict]:
    """Les EPIC clos depuis la naissance de la passe, corps et commentaires reunis."""
    injectee = os.environ.get("SPEC_EPICS_FICHIER")
    if injectee:
        return json.loads(pathlib.Path(injectee).read_text(encoding="utf-8"))

    corpus = []
    for entree in liste_issues(
        ["--label", "epic", "--state", "closed", "--limit", "300", "--json", "number,closedAt"]
    ):
        if (entree.get("closedAt") or "") <= DEPUIS:
            continue
        corpus.append(vue_issue(entree["number"], "number,body,comments"))
    return corpus


def _texte_de(epic: dict) -> str:
    return "\n".join(
        [epic.get("body") or ""] + [(c.get("body") or "") for c in (epic.get("comments") or [])]
    )


def sans_reponse(corpus: list[dict]) -> list[int]:
    """Les EPIC qui PORTENT une trace mais n y ont pas repondu."""
    return [
        e["number"] for e in corpus if MARQUE in _texte_de(e) and not LIGNE.search(_texte_de(e))
    ]


def juger() -> int:
    """La premisse, puis le cliquet, et le code de sortie qui va avec."""
    seuil = cliquet()
    corpus = epics()

    # La PREMISSE, verifiee a chaque passage plutot qu ecrite dans un en-tete (#4948). Ce garde ne
    # juge que les traces qui PORTENT la marque : si elle est renommee, son corpus tombe a zero, il
    # compte zero manquement et passe VERT en n ayant rien lu. Un EPIC clos sans AUCUNE trace est
    # legitime et appartient a ADR 4659 ; ce qui ne l est pas, c est qu AUCUN ne porte la marque.
    portent = sum(1 for e in corpus if MARQUE in _texte_de(e))
    if corpus and portent == 0:
        print(f"REFUS : {len(corpus)} EPIC clos, et AUCUN ne porte « {MARQUE} ».", file=sys.stderr)
        print(
            "Ce garde ne lit que les traces qui portent cette marque : son corpus est vide, et un",
            file=sys.stderr,
        )
        print(
            "compte de zero ne voudrait rien dire. La marque a probablement ete renommee dans",
            file=sys.stderr,
        )
        print("« dev-docs/cycle-de-chantier.md » ; alignez-la ici.", file=sys.stderr)
        return 2

    liste = sans_reponse(corpus)
    compte = len(liste)

    print(f"CLIQUET 4922 | sans réponse à la passe de spécification={compte} | cliquet={seuil}")
    if compte > seuil:
        print()
        print("Un chantier a été clos sans répondre à la passe 10 du cycle.")
        print(
            "Cochez-la dans la trace de l'EPIC : soit le changement OpenSpec est archivé, soit écrivez"
        )
        print(
            "« sans objet » AVEC sa raison. Une capacité métier touchée sans spécification laisse la"
        )
        print("spécification vivante décrire un produit que le code a déjà dépassé.")
        print()
        print(f"EPIC sans réponse : {' '.join(str(n) for n in liste)}")
        return 1
    if compte < seuil:
        print(f"Le dépôt en porte MOINS que son cliquet : descendez-le à {compte} dans l'ADR.")
    return 0


TRACE = "## Clôture de chantier\n- [x] 0. Relecture des ADR"
REPONDU = TRACE + "\n- [x] 10. Changement OpenSpec archivé : **sans objet**, aucune capacité."
ANCIENNE = TRACE + "\n- [x] 10. **ADR** · décisions énumérées, pas fichiers comptés."
PASSE_6 = TRACE + "\n- [x] 6. Tests : sept gardes de méthode verts, dont les deux d'OpenSpec."
ARCHIVAGE = TRACE + "\n- [x] 10. Archivage OpenSpec : **sans objet**."
AUTRE_NUMERO = TRACE + "\n- [x] 12. Changement OpenSpec archivé : fusionné."

# (attendu, libelle, corpus, adr, motif). Le motif n est pas decoratif : sans lui, deux refus
# differents sortent tous deux en 2 et un cas peut passer pour la mauvaise raison (ADR 4918).
CAS = (
    # Le cas qui compte : sans lui, tous les verts de ce garde seraient creux.
    (
        "rouge",
        "un chantier de plus clos sans répondre à la passe fait monter le compte",
        [
            {"number": 1, "body": TRACE, "comments": []},
            {"number": 2, "body": TRACE, "comments": []},
        ],
        "adr",
        "",
    ),
    (
        "ok",
        "le compte égal au cliquet passe",
        [{"number": 1, "body": TRACE, "comments": []}],
        "adr",
        "",
    ),
    ("ok", "le compte SOUS le cliquet passe : un cliquet descend", [], "adr", ""),
    (
        "ok",
        "une passe 10 répondue ne compte pas",
        [
            {"number": 1, "body": TRACE, "comments": []},
            {"number": 2, "body": REPONDU, "comments": []},
        ],
        "adr",
        "",
    ),
    (
        "ok",
        "la réponse dans un COMMENTAIRE compte, c'est là que la trace se colle",
        [
            {"number": 1, "body": TRACE, "comments": []},
            {"number": 2, "body": "x", "comments": [{"body": REPONDU}]},
        ],
        "adr",
        "",
    ),
    (
        "ok",
        "un EPIC SANS trace n'entre pas dans ce compte : il est le gibier d'ADR 4659",
        [
            {"number": 1, "body": TRACE, "comments": []},
            {"number": 2, "body": "rien", "comments": []},
        ],
        "adr",
        "",
    ),
    # Le cas qui a piege l ecriture de ce garde, et la raison pour laquelle il lit le CONTENU.
    (
        "rouge",
        "une ANCIENNE passe 10 (les ADR) ne vaut pas réponse : #4840 a renuméroté",
        [
            {"number": 1, "body": TRACE, "comments": []},
            {"number": 2, "body": ANCIENNE, "comments": []},
        ],
        "adr",
        "",
    ),
    (
        "rouge",
        "une passe 6 qui NOMME OpenSpec sans archiver ne vaut pas réponse (#4938)",
        [
            {"number": 1, "body": TRACE, "comments": []},
            {"number": 2, "body": PASSE_6, "comments": []},
        ],
        "adr",
        "",
    ),
    (
        "ok",
        "la formulation « Archivage OpenSpec » vaut réponse : c'est celle de #4882",
        [
            {"number": 1, "body": TRACE, "comments": []},
            {"number": 2, "body": ARCHIVAGE, "comments": []},
        ],
        "adr",
        "",
    ),
    (
        "ok",
        "un AUTRE numéro portant le même contenu vaut réponse : le numéro ne fait pas foi",
        [
            {"number": 1, "body": TRACE, "comments": []},
            {"number": 2, "body": AUTRE_NUMERO, "comments": []},
        ],
        "adr",
        "",
    ),
    # La premisse, eprouvee plutot qu ecrite (#4948).
    (
        "refus",
        "des EPIC clos dont AUCUN ne porte la marque : corpus vide, on REFUSE",
        [
            {
                "number": 1,
                "body": "## Bilan de chantier\n- [x] 10. Archivage OpenSpec : sans objet",
                "comments": [],
            }
        ],
        "adr",
        "AUCUN ne porte",
    ),
    ("ok", "AUCUN EPIC clos : rien a juger, ce n est pas un corpus vide suspect", [], "adr", ""),
    (
        "refus",
        "une ADR sans cliquet lisible fait REFUSER, pas conclure",
        [{"number": 1, "body": TRACE, "comments": []}],
        "muette",
        "",
    ),
    (
        "refus",
        "une ADR introuvable fait REFUSER aussi",
        [{"number": 1, "body": TRACE, "comments": []}],
        "absente",
        "",
    ),
)


def _auto_test() -> int:
    """Quatorze cas hors ligne, dont cinq qui DOIVENT refuser."""
    import tempfile

    verifie, echecs = cas_d_auto_test_de_forge()
    cas = rouges = 0
    with tempfile.TemporaryDirectory(prefix="vc-spec-") as tmp:
        bac = pathlib.Path(tmp)
        (bac / "adr.md").write_text("ratchet: 1\n", encoding="utf-8")
        (bac / "adr-muette.md").write_text("title: une ADR sans cliquet\n", encoding="utf-8")
        adrs = {
            "adr": bac / "adr.md",
            "muette": bac / "adr-muette.md",
            "absente": bac / "nulle-part.md",
        }

        for attendu, libelle, corpus, adr, motif in CAS:
            cas += 1
            if attendu != "ok":
                rouges += 1
            (bac / "epics.json").write_text(json.dumps(corpus), encoding="utf-8")
            os.environ["SPEC_EPICS_FICHIER"] = str(bac / "epics.json")
            os.environ["SPEC_ADR_FICHIER"] = str(adrs[adr])
            verifie(attendu, libelle, motif, juger)

    for cle in ("SPEC_EPICS_FICHIER", "SPEC_ADR_FICHIER"):
        os.environ.pop(cle, None)

    print()
    print(f"{cas} cas, dont {rouges} qui DOIVENT refuser.")
    if echecs() == 0:
        print("Auto-test concluant : le garde voit une passe de spécification sautée.")
    else:
        print("Auto-test EN ÉCHEC.")
    return echecs()


if __name__ == "__main__":
    if "--auto-test" in sys.argv[1:2]:
        sys.exit(_auto_test())
    sys.exit(juger())
