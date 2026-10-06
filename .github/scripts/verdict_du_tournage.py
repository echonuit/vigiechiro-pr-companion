#!/usr/bin/env python3
"""Ce qu un tournage a vraiment donne : combien de tests ont rougi (#4351, porte du bash en #5231).

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

## Il compte des TESTS, et seulement ceux du tournage

Le nombre annonce est celui que Maven donne au pas qui filme, « Tests run ». Ce n est pas le nombre
de CAS de recette, que l oracle compte et que l atelier ecrit sur la ligne d a cote : un test peut
jouer plusieurs cas, et un cas etre joue par plusieurs tests. Le libelle disait « cas » (#5865).

L atelier joue aussi une classe AVANT de filmer, pour deriver son oracle, et ses rapports restent
dans le dossier lu ici. Elle se nomme en argument, et sort du compte : le verdict annoncait 122 pour
107 tests filmes. Deux garde-fous, parce que ce script existe pour qu un rouge se voie :

- une classe nommee qui ROUGIT reste dans le verdict, comptee et nommee ;
- une classe nommee que rien ne rapporte se DIT, sans quoi la renommer remettrait ses tests dans
  le compte en silence.

Usage : python3 .github/scripts/verdict_du_tournage.py [dossier-des-rapports] [classe-hors-compte ...]
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


def _est(nom: str, classe: str) -> bool:
    """`classe` se nomme en entier ou par son nom court : l atelier ecrit le second."""
    return nom == classe or nom.endswith("." + classe)


def _tests(n: int, joues: bool = True) -> str:
    """« 1 test joué », « 4 tests joués » : le compte s accorde, « cas » le cachait."""
    return f"{n} test{'s' if n > 1 else ''}" + (f" joué{'s' if n > 1 else ''}" if joues else "")


def verdict(dossier: str | pathlib.Path, hors_compte: tuple[str, ...] = ()) -> int:
    """Ce que les rapports disent, et 0 quoi qu ils disent : ce script rapporte, il ne juge pas.

    `hors_compte` nomme les classes que l atelier joue sans les filmer.
    """
    fichiers = sorted(pathlib.Path(dossier).glob("TEST-*.xml"))

    if not fichiers:
        # « Aucun rapport » n est PAS « aucun echec ». Un tournage dont les tests n ont pas demarre
        # rendrait sinon le meme texte qu un tournage parfait, ce qui est exactement le faux vert
        # que ce script existe pour empecher.
        print("⚠️ AUCUN rapport de test : impossible de dire ce que ce tournage a donné.")
        return 0

    total = rouges = sautes = 0
    fautives: list[str] = []
    rapportees: set[str] = set()
    for chemin in fichiers:
        try:
            racine = ET.parse(chemin).getroot()
        except ET.ParseError:
            fautives.append(chemin.name + " (illisible)")
            continue
        tests = int(racine.get("tests") or 0)
        echecs = int(racine.get("failures") or 0) + int(racine.get("errors") or 0)
        nommee = [c for c in hors_compte if _est(racine.get("name") or "", c)]
        rapportees.update(nommee)
        # VERTE, une classe nommee sort du compte. Rouge, elle y reste : l en sortir cacherait le
        # rouge que ce script existe pour montrer.
        if nommee and not echecs:
            continue
        total += tests
        rouges += echecs
        sautes += int(racine.get("skipped") or 0)
        if echecs:
            fautives.append(f"{racine.get('name')} : {echecs} sur {tests}")

    # Dite en fin de verdict, quel qu il soit : elle ne change pas ce que le tournage a donne, elle
    # dit que le NOMBRE ci-dessus compte peut-etre des tests qui n ont rien filme.
    absentes = [c for c in hors_compte if c not in rapportees]

    def dit_les_absentes() -> None:
        for classe in absentes:
            print()
            print(
                f"⚠️ « {classe} » est nommée hors du compte, et aucun rapport ne la porte : si elle a"
            )
            print("été renommée, ses tests sont comptés ci-dessus avec ceux du tournage.")

    if rouges == 0 and not fautives and sautes == 0:
        print(f"✓ {_tests(total)}, aucun rouge.")
        dit_les_absentes()
        return 0

    if rouges == 0 and not fautives:
        # Pas de ✓ : un cas SAUTE n a rien montre. Depuis #4447 un scenario peut abandonner quand
        # la precondition de son geste manque, et mener avec un ✓ ferait lire « tout a ete montre »
        # a qui s arrete au premier signe.
        print(
            f"◻ {_tests(total)}, aucun rouge, mais {sautes} SAUTÉ(S) : leur geste n'a pas eu lieu."
        )
        print()
        print(
            "Un test sauté n'est ni un succès ni un défaut : c'est un geste que le banc n'a pas pu"
        )
        print(
            "jouer, et dont le clip ne montre donc pas ce que le cas promet. Le journal du pas de"
        )
        print("tournage en donne la raison, et l'index ne les compte pas comme couverts.")
        dit_les_absentes()
        return 0

    accord = "a ROUGI" if rouges == 1 else "ont ROUGI"
    print(f"⚠️ **{_tests(rouges, joues=False)} {accord}** sur {total} joués ({sautes} sauté(s)).")
    print()
    for f in fautives:
        print(f"- {f}")
    print()
    print("Leurs clips sont versés comme les autres, et c'est voulu : un cas qui rougit est celui")
    print("qu'on veut regarder. Mais ils ne montrent PAS ce que leur cas promet.")
    dit_les_absentes()
    return 0


# La classe que l atelier joue AVANT de filmer, pour deriver son oracle. Quinze tests, aucun clip.
ORACLE = "fr.univ_amu.iut.recette.CorrespondanceRecetteTest"
_FILME = '<testsuite name="fr.essai.ScenarioTest" tests="4" failures="0" errors="0" skipped="0"/>'
_ORACLE_VERT = f'<testsuite name="{ORACLE}" tests="15" failures="0" errors="0" skipped="0"/>'
_ORACLE_ROUGE = f'<testsuite name="{ORACLE}" tests="15" failures="1" errors="0" skipped="0"/>'

# (nom, motif attendu, rapports, classes nommees hors du compte[, motif INTERDIT])
CAS = (
    (
        "un tournage tout vert le dit",
        "aucun rouge",
        ('<testsuite name="fr.essai.Vert" tests="4" failures="0" errors="0" skipped="0"/>',),
        (),
    ),
    # Le LIBELLE dit ce qui est compte : des tests, pas des cas de recette (#5865). Le compte des
    # cas est celui de l oracle, que l atelier ecrit sur la ligne d a cote.
    (
        "le verdict compte des TESTS et les nomme ainsi",
        "✓ 4 tests joués, aucun rouge.",
        ('<testsuite name="fr.essai.Vert" tests="4" failures="0" errors="0" skipped="0"/>',),
        (),
    ),
    (
        "un échec est compté et nommé",
        "1 test a ROUGI",
        ('<testsuite name="fr.essai.Rouge" tests="3" failures="1" errors="0" skipped="0"/>',),
        (),
    ),
    # Une ERREUR n est pas une defaillance pour surefire, et c est ainsi qu un cas qui explose au
    # montage - le banc qui refuse faute de jeton - passerait sous un compteur qui ne lirait que
    # `failures`.
    (
        "une erreur compte autant qu'une défaillance",
        "1 test a ROUGI",
        ('<testsuite name="fr.essai.Erreur" tests="2" failures="0" errors="1" skipped="0"/>',),
        (),
    ),
    # Le cas du geste ABANDONNE (#4447).
    (
        "un test sauté ne mène pas avec un ✓",
        "◻ 1 test joué, aucun rouge, mais 1 SAUTÉ(S)",
        ('<testsuite name="fr.essai.Saute" tests="1" failures="0" errors="0" skipped="1"/>',),
        (),
    ),
    # Et le controle de l autre bord : sans saut, le ✓ revient.
    (
        "sans saut, le tournage garde son ✓",
        "✓",
        ('<testsuite name="fr.essai.ToutVert" tests="2" failures="0" errors="0" skipped="0"/>',),
        (),
    ),
    # Le controle qui empeche ce script de rassurer sur du vide.
    ("aucun rapport n'est pas aucun échec", "AUCUN rapport", (), ()),
    ("un rapport illisible se dit", "illisible", ('<testsuite name="tronque" tests="1"',), ()),
    # La classe de l ORACLE ne filme rien, et ses rapports restent dans le dossier que ce script
    # lit : le verdict annoncait 122 pour 107 tests filmes, et 20 pour 5 (#5865).
    (
        "sans classe nommée, tout rapport est compté, comme avant",
        "✓ 19 tests joués",
        (_FILME, _ORACLE_VERT),
        (),
    ),
    (
        "une classe nommée hors du compte n'y entre pas",
        "✓ 4 tests joués, aucun rouge.",
        (_FILME, _ORACLE_VERT),
        (ORACLE,),
    ),
    # Le controle de l autre bord du refus ci-dessous : une classe nommee ET rapportee ne se dit
    # pas absente. Sans lui, un avertissement pose a tort sur chaque tournage passerait.
    (
        "et rapportée, elle ne se dit pas absente",
        "✓ 4 tests joués, aucun rouge.",
        (_FILME, _ORACLE_VERT),
        (ORACLE,),
        "aucun rapport ne la porte",
    ),
    (
        "elle se nomme aussi par son nom court",
        "✓ 4 tests joués, aucun rouge.",
        (_FILME, _ORACLE_VERT),
        ("CorrespondanceRecetteTest",),
    ),
    # Les deux chemins de REFUS de l exclusion. Une classe nommee que rien ne rapporte : sans cette
    # ligne, la renommer remettrait ses quinze tests dans le compte sans que rien ne bouge.
    (
        "une classe nommée que rien ne rapporte se dit",
        "« fr.essai.Renommee » est nommée hors du compte, et aucun rapport ne la porte",
        (_FILME, _ORACLE_VERT),
        ("fr.essai.Renommee",),
    ),
    # Et une classe nommee qui ROUGIT : ce script existe pour qu un rouge se voie, l exclure du
    # compte ne doit pas l exclure du verdict.
    (
        "une classe hors du compte qui rougit reste dans le verdict",
        f"- {ORACLE} : 1 sur 15",
        (_FILME, _ORACLE_ROUGE),
        (ORACLE,),
    ),
)


def _auto_test() -> int:
    """Les sept cas de la version bash, le libelle, et les cinq de la classe hors du compte."""
    import contextlib
    import io
    import shutil
    import tempfile

    total = echecs = 0
    print("AUTO-TEST")
    with tempfile.TemporaryDirectory(prefix="vc-verdict-") as tmp:
        rapports = pathlib.Path(tmp) / "rapports"
        for nom, motif, contenus, hors_compte, *reste in CAS:
            interdit = reste[0] if reste else ""
            shutil.rmtree(rapports, ignore_errors=True)
            rapports.mkdir()
            for rang, contenu in enumerate(contenus):
                (rapports / f"TEST-essai-{rang}.xml").write_text(contenu + "\n", encoding="utf-8")
            tampon = io.StringIO()
            with contextlib.redirect_stdout(tampon):
                verdict(rapports, hors_compte)
            obtenu = tampon.getvalue()
            total += 1
            if motif in obtenu and not (interdit and interdit in obtenu):
                print(f"  [OK   ] {nom:<54}")
            elif motif in obtenu:
                print(f"  [ÉCHEC] {nom:<54} -> « {interdit} » ne devait pas paraître")
                echecs += 1
            else:
                premiere = obtenu.splitlines()[0] if obtenu.splitlines() else ""
                print(f"  [ÉCHEC] {nom:<54} -> {premiere}")
                echecs += 1

    print()
    print(f"{total} cas.")
    if echecs != 0:
        print(f"AUTO-TEST EN ÉCHEC ({echecs}) : ne pas se fier au verdict de ce script.")
        return 1
    print("Auto-test concluant.")
    return 0


if __name__ == "__main__":
    if "--auto-test" in sys.argv[1:2]:
        sys.exit(_auto_test())
    sys.exit(
        verdict(
            sys.argv[1] if len(sys.argv) > 1 else "target/surefire-reports",
            tuple(sys.argv[2:]),
        )
    )
