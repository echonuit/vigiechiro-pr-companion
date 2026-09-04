#!/usr/bin/env python3
"""Une issue prise appartient a un chantier, et son corps dit le meme que la forge (#4649, #5210).

La regle vit dans `ouvrir-une-issue` depuis #4644, c est-a-dire dans ce qu un agent lit s il pense a
le lire. C est exactement ainsi qu elle a ete manquee : elle etait deja ecrite a trois endroits le
27 aout 2026, et trois sessions l ont enfreinte le meme jour.

## Ce que ce garde a gagne en #5210

Rattacher demande DEUX gestes : la marque dans le corps, et `gh issue edit --parent` sur la forge.
La version d origine ne lisait que le second. Une issue dont le corps designait un chantier et dont
le parent en designait un autre passait verte, et personne ne savait laquelle des deux disait vrai.
Mesure du 2026-09-04 : deux divergences sur les 150 issues ouvertes, trouvees a la main en cloturant
un chantier, et neuf marques que la forge ne connaissait pas.

## Pourquoi la marque se lit sur une LIGNE A ELLE SEULE

Parce qu une prose qui CITE la forme n en porte pas une. Le defaut s est produit pendant la
correction : les commentaires qui expliquaient la rectification reproduisaient la phrase, et un motif
libre les comptait comme des marques. C est #5032 applique a de la prose - une MENTION n est pas une
DECLARATION.

La forme retenue est **mesuree, pas supposee**. Une premiere hypothese - la marque est en tete de
corps - rendait 15 sur 35 : fausse. La marque est une ligne dont c est le seul contenu, ce qui rend
**35 sur 35**.

## Pourquoi ce garde est en Python

Il etait en bash, et l ADR 5188 dit que le bash disparait : lui ajouter une capacite aurait ete
investir dans ce qui doit partir. La conversion fait descendre le cliquet du corpus shell de 50 a 49,
sa premiere descente.

Usage : verifie_chantier_de_l_issue.py "<corps de la PR>"
        verifie_chantier_de_l_issue.py --auto-test
"""

from __future__ import annotations

import json
import os
import pathlib
import re
import shutil
import subprocess
import sys

SAS = 4562

# Les mots-cles que la forge reconnait sont un fait que l ADR 4546 porte deja, mesure. Ce garde
# l applique plutot que de le reenoncer, et « Ferme #N » reste l affaire de `verifie-corps-pr.sh`,
# qui tient l autre moitie de la meme decision.
FERMETURE = re.compile(
    r"\b(?:close[sd]?|closing|fix(?:e[sd])?|fixing|resolve[sd]?|resolving)\s+#(\d+)",
    re.I,
)

# La marque de rattachement, sur une LIGNE A ELLE SEULE. Voir la docstring : mesure sur le corpus,
# 35 issues sur 35, quand « en tete de corps » n en rendait que 15.
MARQUE = re.compile(r"^\s*Fait partie de #(\d+)\s*\.?\s*$")


def issues_fermees(corps: str) -> list[int]:
    """Les issues que le corps declare fermer, dedoublonnees et triees."""
    return sorted({int(n) for n in FERMETURE.findall(corps or "")})


def _leurre() -> dict | None:
    """Le fichier injecte par l auto-test, ou rien. Sans lui, ses cas exigeraient la forge.

    **Un leurre illisible REFUSE, il ne rend pas un dictionnaire vide.** Le premier portage rendait
    `{}`, donc « AUCUN » pour toute issue, donc un REFUS DE RATTACHEMENT la ou la version bash
    refusait de conclure. Un garde qui transforme sa propre panne en verdict sur le corpus accuse
    l innocent, et c est le defaut que ce fichier existe pour interdire.
    """
    chemin = os.environ.get("CHANTIER_ISSUES_FICHIER")
    if not chemin:
        return None
    try:
        return json.loads(pathlib.Path(chemin).read_text(encoding="utf-8"))
    except (OSError, ValueError) as panne:
        print(
            f"REFUS : le fichier d issues « {chemin} » est illisible ({panne}). "
            "Ce garde ne conclut pas sur ce qu'il n'a pas lu.",
            file=sys.stderr,
        )
        raise SystemExit(2) from panne


def _forge(numero: int, champ: str, filtre: str) -> str:
    """Interroge la forge, ou REFUSE. Un garde ne conclut pas sur ce qu il n a pas lu."""
    if shutil.which("gh") is None:
        print(
            "REFUS : « gh » est absent. Ce garde ne conclut pas sur ce qu'il n'a pas lu.",
            file=sys.stderr,
        )
        raise SystemExit(2)
    rendu = subprocess.run(
        ["gh", "issue", "view", str(numero), "--json", champ, "-q", filtre],
        capture_output=True,
        text=True,
        check=False,
    )
    if rendu.returncode != 0:
        print(f"REFUS : la forge n'a pas répondu pour #{numero}.", file=sys.stderr)
        raise SystemExit(2)
    return rendu.stdout.strip()


def parent_de(numero: int) -> str:
    """Le parent d une issue, « AUCUN » s il n y en a pas."""
    leurre = _leurre()
    if leurre is not None:
        return str(leurre.get(str(numero), "AUCUN"))
    return _forge(numero, "parent", '.parent.number // "AUCUN"') or "AUCUN"


def marque_de(numero: int) -> str:
    """Le chantier que le CORPS de l issue declare, « AUCUNE » s il n en declare pas."""
    leurre = _leurre()
    if leurre is not None:
        return str(leurre.get(f"{numero}:marque", "AUCUNE"))
    corps = _forge(numero, "body", ".body")
    for ligne in corps.split("\n"):
        trouve = MARQUE.match(ligne)
        if trouve:
            return trouve.group(1)
    return "AUCUNE"


def juge(corps: str) -> int:
    """Le verdict, et le code de sortie qui va avec."""
    numeros = issues_fermees(corps)
    if not numeros:
        print("Cette demande de fusion ne ferme aucune issue : rien à juger.")
        return 0

    fautives = []
    for numero in numeros:
        parent = parent_de(numero)
        if parent == "AUCUN":
            fautives.append(f"  #{numero} : aucun chantier")
            continue
        if parent == str(SAS):
            fautives.append(f"  #{numero} : ne pend qu au sas #{SAS}, d ou rien ne se traite")
            continue
        # LA SECONDE SURFACE (issue #5210). Le parent existe ; le corps dit-il le meme ?
        marque = marque_de(numero)
        if marque != "AUCUNE" and marque != parent:
            fautives.append(
                f"  #{numero} : son corps dit #{marque}, la forge dit #{parent}. "
                "Les deux gestes du rattachement ont divergé"
            )

    if fautives:
        print("Une issue prise appartient à un chantier, et celle-ci n en a pas.")
        print()
        print("\n".join(fautives))
        print()
        print("Ouvrez le chantier qui traite sa CAUSE s il n existe pas, puis :")
        print("    gh issue edit <n> --parent <EPIC>")
        print("et faites dire la même chose au corps de l issue.")
        return 1

    print(
        "Chaque issue fermée par cette demande appartient à un chantier, et son corps le confirme."
    )
    return 0


def _auto_test() -> int:
    """Le garde voit une issue sans chantier, ET une issue qui en désigne deux."""
    import tempfile

    echecs = 0
    cas = rouges = 0

    with tempfile.TemporaryDirectory(prefix="vc-5210-") as tmp:
        bac = pathlib.Path(tmp)
        # Les trois cas du 27 aout 2026, reconstitues. Ils ne sont plus lisibles sur la forge :
        # leurs blocs ont ete corriges le 28, les issues sont closes, et leur `parent` d aujourd hui
        # a ete pose par #4829. S en servir en direct reviendrait a mesurer ce travail-ci.
        (bac / "issues.json").write_text(
            json.dumps(
                {
                    "4571": "AUCUN",
                    "4554": "AUCUN",
                    "4617": "AUCUN",
                    "4649": "4643",
                    "4649:marque": "4643",
                    "4795": str(SAS),
                    # LA DIVERGENCE : le parent dit un chantier, le corps en dit un autre.
                    "5055": "5065",
                    "5055:marque": "4562",
                    # Et son contraire : un corps muet ne contredit rien.
                    "5100": "4643",
                }
            ),
            encoding="utf-8",
        )

        def joue(attendu, libelle, corps, fichier=None):
            nonlocal echecs, cas, rouges
            cas += 1
            if attendu != "ok":
                rouges += 1
            env = dict(os.environ)
            env["CHANTIER_ISSUES_FICHIER"] = str(fichier or bac / "issues.json")
            rendu = subprocess.run(
                [sys.executable, __file__, corps],
                capture_output=True,
                text=True,
                env=env,
                check=False,
            )
            obtenu = {0: "ok", 1: "rouge", 2: "refus"}.get(rendu.returncode, str(rendu.returncode))
            if obtenu == attendu:
                print(f"  ✔ {libelle}")
            else:
                print(f"  ✘ {libelle} : attendu {attendu}, obtenu {obtenu}")
                echecs = 1

        # Les cas qui comptent : sans eux, tous les verts ne valent rien.
        joue("rouge", "une issue sans chantier est refusée", "Ce lot fait X.\n\nCloses #4571")
        joue("rouge", "les trois blocs du 27 août, reconstitués", "Closes #4554")
        joue("rouge", "une issue qui ne pend qu au sas est refusée", "Closes #4795")
        joue("ok", "une issue rattachée à un chantier passe", "Closes #4649")
        joue("ok", "une PR qui ne ferme rien n est pas jugée", "Un lot de l EPIC. Refs #4643")
        joue("ok", "un renvoi sans mot-clé ne compte pas", "Voir #4571 pour le detail.")
        joue("rouge", "la casse du mot-clé ne sauve pas", "closes #4571")
        joue("rouge", "« Fixes » compte aussi", "Fixes #4617")
        joue("rouge", "une des deux fermetures suffit à refuser", "Closes #4649\nCloses #4571")

        # LA SECONDE SURFACE (#5210), et son silence.
        joue("rouge", "un corps qui désigne un autre chantier que la forge est vu", "Closes #5055")
        joue("ok", "un corps SANS marque ne contredit rien", "Closes #5100")

        joue(
            "refus",
            "une issue absente de la forge fait REFUSER, pas conclure",
            "Closes #4571",
            bac / "nulle-part.json",
        )

        # LA MARQUE SE LIT SUR UNE LIGNE A ELLE SEULE, et ces trois cas le tiennent. Sans eux, une
        # prose qui cite la forme serait comptee comme une marque - le defaut vécu en corrigeant le
        # corpus, et celui de #5032.
        for libelle, texte, attendu in (
            ("une ligne à elle seule est une marque", "Fait partie de #4805", "4805"),
            ("un point final ne la casse pas", "Fait partie de #4805.", "4805"),
            (
                "une CITATION dans une phrase n en est pas une",
                "la forme Fait partie de #4805 y est",
                None,
            ),
            ("ni une marque suivie d autre chose", "Fait partie de #4805 et le reste", None),
        ):
            cas += 1
            if attendu is None:
                rouges += 1
            trouve = MARQUE.match(texte)
            obtenu = trouve.group(1) if trouve else None
            if obtenu == attendu:
                print(f"  ✔ {libelle}")
            else:
                print(f"  ✘ {libelle} : attendu {attendu}, obtenu {obtenu}")
                echecs = 1

    # L APPEL, et non le verdict (ADR 4331). Les cas ci-dessus injectent tous un leurre et n exercent
    # jamais le chemin reel. Celui-ci le lance avec `gh` hors du PATH : aucun reseau, et il rougit en
    # une milliseconde. Sans lui, la fonction qui interroge n est eprouvee par rien.
    cas += 1
    rouges += 1
    env = {k: v for k, v in os.environ.items() if k != "CHANTIER_ISSUES_FICHIER"}
    env["PATH"] = ""
    rendu = subprocess.run(
        [sys.executable, __file__, "Closes #4571"],
        capture_output=True,
        text=True,
        env=env,
        check=False,
    )
    if rendu.returncode == 2 and "est absent" in rendu.stderr:
        print("  ✔ sans « gh », l appel REFUSE au lieu de conclure")
    else:
        print(f"  ✘ sans « gh », l appel REFUSE au lieu de conclure : code {rendu.returncode}")
        echecs = 1

    print()
    print(f"{cas} cas, dont {rouges} qui DOIVENT refuser.")
    print(
        "Auto-test concluant : le garde voit une issue sans chantier, et un corps qui en désigne "
        "un autre."
        if not echecs
        else "Auto-test EN ÉCHEC."
    )
    return echecs


if __name__ == "__main__":
    if "--auto-test" in sys.argv:
        sys.exit(_auto_test())
    sys.exit(juge(sys.argv[1] if len(sys.argv) > 1 else ""))
