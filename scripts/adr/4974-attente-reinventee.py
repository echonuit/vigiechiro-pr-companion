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
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _commun import TESTS_ANCRES, rapporte

ADR = "4974"

# Le corpus s IMPORTE, il ne se recopie pas : un chemin ecrit en clair rend le garde invisible a la
# liste que `verifie_corpus_declare.py` derive de ce que chacun importe (ADR 4586).
RACINE = TESTS_ANCRES

# `Attente` EST l attente partagee : elle appelle `waitFor` par construction.
EXEMPTES = {"Attente.java"}

# Le corps d une methode privee, quel que soit son NOM et quel que soit son type de retour.
SIGNATURE = re.compile(r"^[ \t]*private (?:static )?[\w<>\[\], ]+ (\w+)\(", re.M)
# `waitFor` attend une CONDITION, `waitForAsyncFx` execute une ACTION sur le fil : deux gestes,
# une seule dette, et `Attente` porte les deux depuis #4997. Ne compter que le premier serait
# le contournement par renommage que cette ADR existe pour empecher.
SONDE = re.compile(r"WaitForAsyncUtils\.(waitFor|waitForAsyncFx)\(")

# Une methode plus longue que cela n est plus une aide : la lecture s arrete et le cas se voit a l
# oeil. Borner evite qu un fichier pathologique fasse lire tout le reste du corps de la classe.
LIGNES_MAX = 60


def corpsDe(texte: str, debut: int) -> str:
    """Le corps de la methode qui commence a `debut`, accolades equilibrees."""
    profondeur, lignes = 0, []
    for ligne in texte[debut:].split("\n"):
        lignes.append(ligne)
        profondeur += ligne.count("{") - ligne.count("}")
        if profondeur == 0 and any("{" in vue for vue in lignes):
            break
        if len(lignes) > LIGNES_MAX:
            break
    return "\n".join(lignes)


# Une ligne de COMMENTAIRE qui cite l appel n est pas un appel. Sans cela, la javadoc d
# `AttenteAvantClic` qui explique pourquoi elle rattrape comptait comme une attente reinventee.
COMMENTAIRE = re.compile(r"^\s*(///|//|\*|/\*)")


def suspects(racine: pathlib.Path = RACINE) -> list[str]:
    """Tout appel a `waitFor` hors de l aide partagee, une entree par site.

    #4845 a elargi la population : elle ne se limite plus aux methodes PRIVEES. Une attente ecrite
    en clair dans un cas de test tait exactement la meme chose, et la restriction ne tenait qu a la
    facon dont le defaut avait ete trouve.
    """
    trouves = []
    for fichier in sorted(racine.rglob("*.java")):
        if fichier.name in EXEMPTES:
            continue
        for rang, ligne in enumerate(fichier.read_text(encoding="utf-8").splitlines(), 1):
            if SONDE.search(ligne) and not COMMENTAIRE.match(ligne):
                trouves.append(f"{fichier.name}:{rang}")
    return trouves


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


if __name__ == "__main__":
    if "--auto-test" in sys.argv:
        sys.exit(_autoTest())
    sys.exit(
        rapporte(ADR, "attentes réinventées : un `waitFor` hors de l'aide partagée", suspects())
    )
