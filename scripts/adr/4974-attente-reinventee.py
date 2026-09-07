#!/usr/bin/env python3
"""Cliquet sur les attentes reinventees : une methode privee dont le corps appelle `waitFor`.

Le depot porte `Attente`, qui attend une condition ET dit ce qu'elle attendait en expirant. Sans ce
message, l echec arrive plus tard sur l assertion du banc, qui accuse le code alors que c est la mise
en place qui n a pas eu lieu (ADR 2213).

**Ce que ce cliquet tient, et pourquoi il ne lit pas les noms.** #4847 avait retire treize attentes
privees nommees `attendre`, et voulait un cliquet sur cette forme. La mesure l a refuse deux fois :
les cinq methodes `attendre` qui restaient etaient toutes legitimes - un `CountDownLatch`, deux
cadencements, un ralentisseur - et un garde sur le nom les aurait toutes interdites. Surtout, il
manquait NEUF reinventions ecrites sous d autres noms, dont `doubleClicVersPassage`, qui tient le
banc le plus instable du depot. Un garde qui lit un nom se contourne en renommant.

**Ce qu il ne tient pas.** Que l attente soit justifiee. Une aide qui attend en chemin n est pas
fautive ; ce qui l est, c est d attendre sans dire quoi. Le cliquet borne donc le nombre de sites qui
sondent en propre, et chaque survivant porte dans sa javadoc la raison d en etre un.

**Les deux survivants, et leur raison.** Les deux `doubleClicVersPassage` sont des boucles de
REPRISE : leur expiration est rattrapee pour retenter, et c est leur `throw` final qui parle, en
joignant les bornes observees de la cellule. Convertir celle de `ParcoursSitesVersPassage` ferait
sortir un `AssertionError` du premier essai, la ou son `catch` n attend qu une `TimeoutException` :
la boucle n aurait plus que l apparence d une reprise.
"""

from __future__ import annotations

import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from _commun import TESTS_ANCRES, rapporte, sort_si_contrat_demande
from _commun.arbre import LecteurAbsent, arbre, noeuds_de_type, zones_illisibles

ADR = "4974"

# Le corpus s IMPORTE, il ne se recopie pas : un chemin ecrit en clair rend le garde invisible a la
# liste que `verifie_corpus_declare.py` derive de ce que chacun importe (ADR 4586).
RACINE = TESTS_ANCRES

# `Attente` EST l attente partagee : elle appelle `waitFor` par construction.
EXEMPTES = {"Attente.java"}

# `waitFor` attend une CONDITION, `waitForAsyncFx` execute une ACTION sur le fil : deux gestes,
# une seule dette, et `Attente` porte les deux depuis #4997. Ne compter que le premier serait
# le contournement par renommage que cette ADR existe pour empecher.
SONDES = {"waitFor", "waitForAsyncFx"}
PORTEUR = "WaitForAsyncUtils"


def fichiers(racine: pathlib.Path = RACINE) -> list[pathlib.Path]:
    """Les unites que ce garde LIT, extraites pour que `lus` les compte (issue #5015).

    Le parcours vivait dans `suspects()`, qui ne rendait que ce qu il RETENAIT : un
    ciblage manque donnait zero suspect sur zero fichier, et ce zero passait pour un succes.
    """
    return sorted(racine.rglob("*.java"))


def analyse(racine: pathlib.Path = RACINE) -> tuple[list[str], list[str]]:
    """UNE passe sur le corpus, DEUX sorties : les suspects, et ce que la grammaire n a pas lu.

    **La lecture se fait par la STRUCTURE depuis #5430, et ce n est pas de l elegance.** La version
    d avant cherchait `WaitForAsyncUtils.waitFor(` par une expression reguliere, ligne par ligne, et
    ecartait les commentaires en regardant si la ligne COMMENCE par `//`, `*` ou `/*`. Cette
    heuristique se trompait dans les deux directions, mesure sur temoin le 2026-09-07 :

        String aide = "utilisez WaitForAsyncUtils.waitFor(1, S, cond)";   -> compte, a tort
        /*
           WaitForAsyncUtils.waitFor(1, S, cond);                        -> compte, a tort
         */

    Une chaine de caracteres n est pas un appel, et la ligne MEDIANE d un commentaire de bloc ne
    porte aucun marqueur. L arbre connait les deux sans heuristique : un `method_invocation` est un
    appel, le reste ne l est pas.
    """
    trouves, zones_dites = [], []
    for fichier in fichiers(racine):
        if fichier.name in EXEMPTES:
            continue
        racine_ast = arbre(fichier.read_bytes()).root_node
        zones_dites += [f"{fichier.name}:{d}-{b}" for d, b in zones_illisibles(racine_ast)]
        for appel in noeuds_de_type(racine_ast, {"method_invocation"}):
            nom = appel.child_by_field_name("name")
            objet = appel.child_by_field_name("object")
            if nom is None or objet is None:
                continue
            if nom.text.decode() in SONDES and objet.text.decode() == PORTEUR:
                trouves.append(f"{fichier.name}:{appel.start_point[0] + 1}")
    return trouves, zones_dites


def suspects(racine: pathlib.Path = RACINE) -> list[str]:
    """Tout appel a `waitFor` hors de l aide partagee, une entree par site.

    #4845 a elargi la population : elle ne se limite plus aux methodes PRIVEES. Une attente ecrite
    en clair dans un cas de test tait exactement la meme chose, et la restriction ne tenait qu a la
    facon dont le defaut avait ete trouve.
    """
    return analyse(racine)[0]


def non_lus(racine: pathlib.Path = RACINE) -> list[str]:
    """Les zones qu aucune grammaire n a su lire, nommees pour etre DITES et non reparees.

    Sans elles, un garde qui lit par l arbre rend zero suspect sur une population amputee, et ce
    zero ressemble a un succes : le defaut que #5007 a corrige en faisant compter les unites LUES.
    """
    return analyse(racine)[1]


def _autoTest() -> int:
    import tempfile

    cas = []
    with tempfile.TemporaryDirectory() as brut:
        r = pathlib.Path(brut)
        sonde = "        WaitForAsyncUtils.waitFor(1, S, () -> vrai());\n"

        # Une aide privee qui sonde : le defaut d origine, quel que soit son NOM (#4974).
        (r / "A.java").write_text(
            "class A {\n    private void ouvrirLaFiche() {\n" + sonde + "    }\n}\n",
            encoding="utf-8",
        )
        cas.append(("un nom quelconque est vu", suspects(r) == ["A.java:3"]))
        (r / "A.java").write_text(
            "class A {\n    private void patienter() {\n" + sonde + "    }\n}\n", encoding="utf-8"
        )
        cas.append(("renommer ne soustrait pas", suspects(r) == ["A.java:3"]))

        # LA population elargie par #4845 : une attente ecrite en clair dans un CAS DE TEST tait
        # exactement la meme chose. La restriction aux methodes privees ne tenait qu a la facon dont
        # le defaut avait ete trouve.
        (r / "B.java").write_text(
            "class B {\n    @Test\n    void un_cas() {\n" + sonde + "    }\n}\n", encoding="utf-8"
        )
        cas.append(("un cas de test compte aussi", "B.java:4" in suspects(r)))

        # LE temoin de #4997 : `waitForAsyncFx` est la meme dette sous un autre nom. Sans lui, le
        # garde ne comptait que `waitFor` et sept sites lui echappaient - le contournement par
        # renommage que cette ADR existe pour empecher, arrive a son propre garde.
        (r / "F.java").write_text(
            "class F {\n    private void surFx(Runnable a) {\n"
            "        WaitForAsyncUtils.waitForAsyncFx(5_000, a);\n    }\n}\n",
            encoding="utf-8",
        )
        cas.append(("waitForAsyncFx compte aussi", "F.java:3" in suspects(r)))

        # Le sens NEGATIF : une ligne de COMMENTAIRE qui cite l appel n est pas un appel. Sans ce
        # temoin, la javadoc d `AttenteAvantClic` expliquant pourquoi elle rattrape comptait comme
        # une reinvention, et le cliquet valait un de trop.
        (r / "C.java").write_text(
            "class C {\n    /// Un `WaitForAsyncUtils.waitFor(...)` nu ne dit rien.\n"
            "    void rien() {}\n}\n",
            encoding="utf-8",
        )
        cas.append(("une citation en commentaire ne compte pas", "C.java:2" not in suspects(r)))

        # Un sleep n attend aucune condition.
        (r / "D.java").write_text(
            "class D {\n    private void dormir() {\n"
            "        WaitForAsyncUtils.sleep(350, MS);\n    }\n}\n",
            encoding="utf-8",
        )
        cas.append(("un sleep n est pas une attente", "D.java:3" not in suspects(r)))

        # `waitForFxEvents` vide la file sans attendre de condition.
        (r / "E.java").write_text(
            "class E {\n    private void vider() {\n"
            "        WaitForAsyncUtils.waitForFxEvents();\n    }\n}\n",
            encoding="utf-8",
        )
        cas.append(("waitForFxEvents n est pas une sonde", "E.java:3" not in suspects(r)))

        # L aide partagee est exemptee, sinon le cliquet compterait le remede.
        (r / "Attente.java").write_text(
            "class Attente {\n    static void que() {\n" + sonde + "    }\n}\n", encoding="utf-8"
        )
        cas.append(
            (
                "l aide partagee est exemptee",
                not any(s.startswith("Attente.java") for s in suspects(r)),
            )
        )

        # LES DEUX CAS QUE LA LECTURE PAR MOTIF RATAIT, et qui sont la raison de #5430. Mesures
        # sur le garde d avant migration le 2026-09-07 : il rendait `G.java:3` et `H.java:4`.
        #
        # Une CHAINE qui cite l appel n est pas un appel. L heuristique d avant n ecartait que les
        # lignes COMMENCANT par un marqueur de commentaire, et une chaine n en porte aucun.
        (r / "G.java").write_text(
            "class G {\n    void message() {\n"
            '        String aide = "utilisez WaitForAsyncUtils.waitFor(1, S, cond)";\n'
            "    }\n}\n",
            encoding="utf-8",
        )
        cas.append(
            ("une chaine qui cite l appel n est pas un appel", "G.java:3" not in suspects(r))
        )

        # La ligne MEDIANE d un commentaire de bloc ne porte ni `//`, ni `*`, ni `/*`. C est le
        # trou de l heuristique : elle ne regardait que le DEBUT de la ligne.
        (r / "H.java").write_text(
            "class H {\n    /*\n       Ancienne mise en place, retiree en #4847 :\n"
            "       WaitForAsyncUtils.waitFor(1, S, cond);\n     */\n"
            "    void propre() {}\n}\n",
            encoding="utf-8",
        )
        cas.append(
            ("une ligne mediane de commentaire de bloc non plus", "H.java:4" not in suspects(r))
        )

        # ET LE CONTRASTE, sans lequel les deux cas ci-dessus passeraient sur un garde qui ne
        # trouve plus rien du tout. Un garde muet satisfait toutes les negations.
        (r / "I.java").write_text(
            "class I {\n    void vrai() {\n" + sonde + "    }\n}\n", encoding="utf-8"
        )
        cas.append(("et le vrai appel, lui, est toujours vu", "I.java:3" in suspects(r)))

    for nom, ok in cas:
        print(f"  {'✔' if ok else '✘'} {nom}")
    rates = [n for n, ok in cas if not ok]
    if rates:
        print(
            f"\n{len(rates)} cas en échec : le cliquet ne tient pas ce qu'il annonce.",
            file=sys.stderr,
        )
        return 1
    print(f"\n{len(cas)} cas : il voit une attente hors de l'aide partagée, sous ses deux noms.")
    return 0


CONTRAT = {
    "geste": "attente reinventee : un waitFor hors de l aide partagee",
    "population": "TESTS",
    "dispositif": "cliquet",
    "seuil": "5, polarite=descend",
    "temoin": "scripts/adr/4974-attente-reinventee.py --auto-test",
    "decision": "ADR 4974",
    # Lire par l arbre coute, et #5400 retire du temps a la batterie en ce moment meme. Declarer les
    # chemins rend la hausse indolore sur toute demande qui ne touche pas de Java (ADR 5340).
    #
    # **Un `chemins` INCOMPLET est plus dangereux qu un `chemins` absent** : il tait le garde en
    # silence, la ou son absence le fait LANCER. Quatre lignes, et chacune a sa raison :
    #
    #  - `src/test/java/**` SEUL, parce que ce garde lit `TESTS_ANCRES` et rien d autre ;
    #  - **ce fichier meme** : `batterie.engage()` confronte les `chemins` au diff sans regle
    #    particuliere sur la source du garde, donc sans cette ligne une demande qui REECRIT ce
    #    cliquet ne le lance pas ;
    #  - `scripts/_commun/**`, ou vivent `rapporte`, `cliquet` et le lecteur d arbre dont ce garde
    #    delegue desormais sa question centrale ;
    #  - la ligne `ratchet:` de sa propre decision, que `resserre_cliquets.py` deplace.
    "chemins": """
src/test/java/**
scripts/adr/4974-attente-reinventee.py
scripts/_commun/**
dev-docs/decisions/4974-un-garde-qui-lit-un-nom-se-contourne-en-renommant.md
""",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        sys.exit(_autoTest())
    # UNE passe, dont sortent le verdict et ce que la grammaire n a pas su lire. Ce dernier se DIT
    # sur la sortie d erreur avant le verdict : le compte est nul aujourd hui, et le jour ou il ne
    # le sera plus, le silence serait un faux vert.
    try:
        listes, zones = analyse()
    except LecteurAbsent as absent:
        # Un REFUS, pas une trace. Une `ModuleNotFoundError` nue ressemble a un defaut du changement
        # en cours ; le message dit ce qui manque ET quoi faire.
        raise SystemExit(str(absent)) from absent
    for zone in zones:
        print(f"zone non lue par la grammaire : {zone}", file=sys.stderr)
    sys.exit(
        rapporte(
            ADR,
            "attentes réinventées : un `waitFor` hors de l'aide partagée",
            listes,
            lus=len(fichiers()),
        )
    )
