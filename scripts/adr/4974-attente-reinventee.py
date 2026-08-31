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
from _commun import rapporte  # noqa: E402

ADR = "4974"
RACINE = pathlib.Path(__file__).resolve().parents[2] / "src" / "test" / "java"

# `Attente` EST l attente partagee : elle appelle `waitFor` par construction.
EXEMPTES = {"Attente.java"}

# Le corps d une methode privee, quel que soit son NOM et quel que soit son type de retour.
SIGNATURE = re.compile(r"^[ \t]*private (?:static )?[\w<>\[\], ]+ (\w+)\(", re.M)
SONDE = re.compile(r"WaitForAsyncUtils\.waitFor\(")

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


def suspects(racine: pathlib.Path = RACINE) -> list[str]:
    """Les methodes privees qui sondent en propre, une ligne par site."""
    trouves = []
    for fichier in sorted(racine.rglob("*.java")):
        if fichier.name in EXEMPTES:
            continue
        texte = fichier.read_text(encoding="utf-8")
        for debut in SIGNATURE.finditer(texte):
            if SONDE.search(corpsDe(texte, debut.start())):
                trouves.append(f"{fichier.name}#{debut.group(1)}")
    return trouves


def _autoTest() -> int:
    import tempfile

    cas = []
    with tempfile.TemporaryDirectory() as brut:
        r = pathlib.Path(brut)

        # Une aide privee qui sonde : c est le defaut, quel que soit son nom.
        (r / "A.java").write_text(
            "class A {\n    private void ouvrirLaFiche() {\n"
            "        WaitForAsyncUtils.waitFor(1, S, () -> vrai());\n    }\n}\n",
            encoding="utf-8")
        cas.append(("un nom quelconque est vu", suspects(r) == ["A.java#ouvrirLaFiche"]))

        # LE temoin qui dit pourquoi ce cliquet ne lit pas les noms : renommer ne doit rien changer.
        (r / "A.java").write_text(
            "class A {\n    private void patienter() {\n"
            "        WaitForAsyncUtils.waitFor(1, S, () -> vrai());\n    }\n}\n",
            encoding="utf-8")
        cas.append(("renommer ne soustrait pas", suspects(r) == ["A.java#patienter"]))

        # Une methode PUBLIQUE n est pas une reinvention privee : c est peut-etre l aide partagee.
        (r / "B.java").write_text(
            "class B {\n    public void que() {\n"
            "        WaitForAsyncUtils.waitFor(1, S, () -> vrai());\n    }\n}\n",
            encoding="utf-8")
        cas.append(("une methode publique reste dehors", "B.java#que" not in suspects(r)))

        # Le sens NEGATIF : une aide privee qui n attend PAS ne se compte pas. Sans ce temoin, un
        # garde qui rendrait toutes les methodes privees paraitrait juste.
        (r / "C.java").write_text(
            "class C {\n    private void attendreRecherche() {\n"
            "        WaitForAsyncUtils.sleep(350, MS);\n    }\n}\n",
            encoding="utf-8")
        cas.append(("un sleep n est pas une attente", "C.java#attendreRecherche" not in suspects(r)))

        # `waitForFxEvents` vide la file sans attendre de condition : il ne se compte pas non plus.
        (r / "D.java").write_text(
            "class D {\n    private void vider() {\n"
            "        WaitForAsyncUtils.waitForFxEvents();\n    }\n}\n",
            encoding="utf-8")
        cas.append(("waitForFxEvents n est pas une sonde", suspects(r) == ["A.java#patienter"]))

        # L aide partagee elle-meme est exemptee, sinon le cliquet compterait le remede.
        (r / "Attente.java").write_text(
            "class Attente {\n    private static void interne() {\n"
            "        WaitForAsyncUtils.waitFor(1, S, () -> vrai());\n    }\n}\n",
            encoding="utf-8")
        cas.append(("l aide partagee est exemptee", "Attente.java#interne" not in suspects(r)))

        # Un `waitFor` HORS de toute methode privee, dans un test public, ne releve pas de ce cliquet.
        (r / "E.java").write_text(
            "class E {\n    @Test\n    void un_cas() {\n"
            "        WaitForAsyncUtils.waitFor(1, S, () -> vrai());\n    }\n}\n",
            encoding="utf-8")
        cas.append(("un cas de test n est pas une aide", len(suspects(r)) == 1))

    for nom, ok in cas:
        print(f"  {'✔' if ok else '✘'} {nom}")
    rates = [n for n, ok in cas if not ok]
    if rates:
        print(f"\n{len(rates)} cas en échec : le cliquet ne tient pas ce qu'il annonce.", file=sys.stderr)
        return 1
    print(f"\n{len(cas)} cas : il voit une sonde privée sous n'importe quel nom, et rien d'autre.")
    return 0


if __name__ == "__main__":
    if "--auto-test" in sys.argv:
        sys.exit(_autoTest())
    sys.exit(rapporte(ADR, "attentes réinventées : une méthode privée qui sonde en propre", suspects()))
