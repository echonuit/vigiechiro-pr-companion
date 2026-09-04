#!/usr/bin/env python3
"""Ce qu un tournage a vraiment donne : combien de cas ont rougi (#4351, porte du bash en #5231).

## Le defaut qu il ferme

Le tournage lance ses tests avec `-Dmaven.test.failure.ignore=true`, et c est delibere : on veut les
CLIPS. Un cas qui rougit produit son clip comme les autres, et c est meme celui-la qu on veut
regarder. L oracle prend ensuite son verdict sur les cas INDEXES, pas sur leur succes.

Consequence mesuree sur le run 32696587259, tire expres avec un jeton revoque : le scenario a rougi,
le job est reste VERT, et le clip du cas echoue s est verse sur la pre-version sans aucune marque. Le
seul endroit ou l echec existait etait une ligne de Maven dans un journal de trois mille.

Ce script ne juge pas - il RAPPORTE, la ou on regarde.

## Il lit le XML, jamais le `.txt`

Les rapports `.txt` de surefire mentent sur les classes a `@Nested` : ils annoncent « Tests run: 0 »
et rendent 0 alors que des cas ont tourne. Le XML porte les attributs `tests`, `failures`, `errors`
et `skipped` par classe, et c est la seule source qui ne se trompe pas.

## Ce qu il ne fait pas

Il ne fait jamais rougir le tournage : un run rouge sur un cas rouge reviendrait sur
`failure.ignore`, dont les raisons tiennent. Il rend un texte, et l appelant decide ou le poser.

Usage : python3 .github/scripts/verdict_du_tournage.py [dossier-des-rapports]
        python3 .github/scripts/verdict_du_tournage.py --auto-test
"""

from __future__ import annotations

import pathlib
import sys
import xml.etree.ElementTree as ET

# La sortie de Python suit l encodage de la CONSOLE, et sous Windows c est cp1252, ou le « ✓ » de la
# ligne de verdict n existe pas. Le tournage `windows-latest` mourait donc sur un caractere
# d ornement, et le rouge accusait le banc alors que les clips etaient bien la (#5195). Reconfigurer
# ici plutot que de poser `PYTHONIOENCODING` sur l etape : ce script est appele de plusieurs
# endroits, et un remede porte par l appelant s oublie au prochain appel.
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")


def verdict(dossier: str | pathlib.Path) -> int:
    """Ce que les rapports disent, et 0 quoi qu ils disent : ce script rapporte, il ne juge pas."""
    fichiers = sorted(pathlib.Path(dossier).glob("TEST-*.xml"))

    if not fichiers:
        # « Aucun rapport » n est PAS « aucun echec ». Un tournage dont les tests n ont pas demarre
        # rendrait sinon le meme texte qu un tournage parfait, ce qui est exactement le faux vert
        # que ce script existe pour empecher.
        print("⚠️ AUCUN rapport de test : impossible de dire ce que ce tournage a donné.")
        return 0

    total = rouges = sautes = 0
    fautives: list[str] = []
    for chemin in fichiers:
        try:
            racine = ET.parse(chemin).getroot()
        except ET.ParseError:
            fautives.append(chemin.name + " (illisible)")
            continue
        tests = int(racine.get("tests") or 0)
        echecs = int(racine.get("failures") or 0) + int(racine.get("errors") or 0)
        total += tests
        rouges += echecs
        sautes += int(racine.get("skipped") or 0)
        if echecs:
            fautives.append(f"{racine.get('name')} : {echecs} sur {tests}")

    if rouges == 0 and not fautives and sautes == 0:
        print(f"✓ {total} cas joués, aucun rouge.")
        return 0

    if rouges == 0 and not fautives:
        # Pas de ✓ : un cas SAUTE n a rien montre. Depuis #4447 un scenario peut abandonner quand
        # la precondition de son geste manque, et mener avec un ✓ ferait lire « tout a ete montre »
        # a qui s arrete au premier signe.
        print(
            f"◻ {total} cas joués, aucun rouge, mais {sautes} SAUTÉ(S) : leur geste n'a pas eu lieu."
        )
        print()
        print(
            "Un cas sauté n'est ni un succès ni un défaut : c'est un geste que le banc n'a pas pu"
        )
        print(
            "jouer, et dont le clip ne montre donc pas ce que le cas promet. Le journal du pas de"
        )
        print("tournage en donne la raison, et l'index ne les compte pas comme couverts.")
        return 0

    print(f"⚠️ **{rouges} cas ont ROUGI** sur {total} joués ({sautes} sauté(s)).")
    print()
    for f in fautives:
        print(f"- {f}")
    print()
    print("Leurs clips sont versés comme les autres, et c'est voulu : un cas qui rougit est celui")
    print("qu'on veut regarder. Mais ils ne montrent PAS ce que leur cas promet.")
    return 0


# (nom, motif attendu, contenu du rapport ou vide)
CAS = (
    (
        "un tournage tout vert le dit",
        "aucun rouge",
        '<testsuite name="fr.essai.Vert" tests="4" failures="0" errors="0" skipped="0"/>',
    ),
    (
        "un échec est compté et nommé",
        "1 cas ont ROUGI",
        '<testsuite name="fr.essai.Rouge" tests="3" failures="1" errors="0" skipped="0"/>',
    ),
    # Une ERREUR n est pas une defaillance pour surefire, et c est ainsi qu un cas qui explose au
    # montage - le banc qui refuse faute de jeton - passerait sous un compteur qui ne lirait que
    # `failures`.
    (
        "une erreur compte autant qu'une défaillance",
        "1 cas ont ROUGI",
        '<testsuite name="fr.essai.Erreur" tests="2" failures="0" errors="1" skipped="0"/>',
    ),
    # Le cas du geste ABANDONNE (#4447).
    (
        "un cas sauté ne mène pas avec un ✓",
        "SAUTÉ(S)",
        '<testsuite name="fr.essai.Saute" tests="1" failures="0" errors="0" skipped="1"/>',
    ),
    # Et le controle de l autre bord : sans saut, le ✓ revient.
    (
        "sans saut, le tournage garde son ✓",
        "✓",
        '<testsuite name="fr.essai.ToutVert" tests="2" failures="0" errors="0" skipped="0"/>',
    ),
    # Le controle qui empeche ce script de rassurer sur du vide.
    ("aucun rapport n'est pas aucun échec", "AUCUN rapport", ""),
    ("un rapport illisible se dit", "illisible", '<testsuite name="tronque" tests="1"'),
)


def _auto_test() -> int:
    """Les sept cas de la version bash, dont deux controles negatifs."""
    import contextlib
    import io
    import shutil
    import tempfile

    total = echecs = 0
    print("AUTO-TEST")
    with tempfile.TemporaryDirectory(prefix="vc-verdict-") as tmp:
        rapports = pathlib.Path(tmp) / "rapports"
        for nom, motif, contenu in CAS:
            shutil.rmtree(rapports, ignore_errors=True)
            rapports.mkdir()
            if contenu:
                (rapports / "TEST-essai.xml").write_text(contenu + "\n", encoding="utf-8")
            tampon = io.StringIO()
            with contextlib.redirect_stdout(tampon):
                verdict(rapports)
            obtenu = tampon.getvalue()
            total += 1
            if motif in obtenu:
                print(f"  [OK   ] {nom:<54}")
            else:
                premiere = obtenu.splitlines()[0] if obtenu.splitlines() else ""
                print(f"  [ÉCHEC] {nom:<54} -> {premiere}")
                echecs += 1

    print()
    print(f"{total} cas, dont 2 contrôles négatifs.")
    if echecs != 0:
        print(f"AUTO-TEST EN ÉCHEC ({echecs}) : ne pas se fier au verdict de ce script.")
        return 1
    print("Auto-test concluant.")
    return 0


if __name__ == "__main__":
    if "--auto-test" in sys.argv[1:2]:
        sys.exit(_auto_test())
    sys.exit(verdict(sys.argv[1] if len(sys.argv) > 1 else "target/surefire-reports"))
