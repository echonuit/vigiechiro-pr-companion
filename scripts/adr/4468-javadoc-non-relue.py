#!/usr/bin/env python3
"""Cliquet sur la javadoc que personne n a encore relue, ou qui a change depuis.

Le depot porte de la javadoc dans ses 1 922 fichiers Java, sans exception. Une partie
raconte un depot qui n existe plus, une autre paraphrase la signature qu elle surmonte, une autre
encore a perdu ses accents ou fusionne deux blocs sur un seul membre. Rien de tout cela ne se voit
d un motif : il faut ouvrir le fichier et lire.

**Ce que ce cliquet tient, exactement.** Le nombre de fichiers Java qui ne figurent pas au
manifeste `scripts/methode/relus.txt` avec l empreinte de leur javadoc du jour. Il tient donc deux
faits, et deux seulement :

- aucun fichier neuf n echappe a la relecture : il arrive absent du manifeste, donc suspect ;
- aucune javadoc relue ne se reecrit en douce : son empreinte change, le fichier redevient suspect,
  et il faut le relire pour le remarquer.

**Ce qu il ne tient pas, et le dire fait partie du garde.** Que la lecture ait eu lieu. Une ligne du
manifeste est une affirmation humaine - « j ai ouvert ce fichier et juge chacun de ses blocs » - et
aucun script ne peut la verifier. Le cliquet borne donc ce qui reste a affirmer, pas la sincerite de
ce qui l a ete. C est ce qui le classe `probable` et non `certaine`.

**Pourquoi le grain du FICHIER, et non de la ligne de prose.** Le cliquet A30, lui, compte les
lignes, parce que raccourcir un bloc est le travail qu il mesure. Ici le travail est la LECTURE, et
son unite est le fichier : un fichier entre au manifeste quand tous ses blocs ont ete juges, jamais
avant. Compter les lignes non lues aurait de plus une issue perverse - effacer de la prose sans
l avoir lue ferait baisser le compte, et le cliquet recompenserait la suppression a l aveugle.

**Les deux arbres Java, et pourquoi.** Ce cliquet a d abord borne la seule production : ouvrir aux
tests avant d avoir solde la production aurait etale la dette au lieu de la resorber. Une fois la
production a zero, l exclusion ne nommait plus aucun repreneur - or une exclusion nomme le sien, ou
c est un trou (ADR « Une dette qu'on migre au fil de l'eau se tient par un cliquet, et toute
exclusion nomme son repreneur »). Les 743 fichiers de `src/test/java` portaient 8 259 lignes de
javadoc que rien ne comptait, alors que l article A31 regit « la javadoc et les commentaires du
code », sans restriction a la production. Ils sont donc entres dans la meme population.

Le cliquet A30, lui, borne toujours la seule production : c est une autre decision, et elle n a pas
ete prise.

**L empreinte n est pas redefinie ici** : elle est empruntee au compteur qui ecrit le manifeste. Deux
copies auraient fini par diverger, et le manifeste aurait alors dit autre chose que ce que ce garde
verifie.
"""

import hashlib
import importlib.util
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _commun import rapporte  # noqa: E402

# Le numero, et non le slug : ici l identite d une ADR est son numero.
ADR = "4468"

RACINE = pathlib.Path(__file__).resolve().parents[2]
MANIFESTE = RACINE / "scripts" / "methode" / "relus.txt"


def _compteur():
    """Le compteur qui ECRIT le manifeste, charge par chemin (son nom porte un tiret)."""
    chemin = RACINE / "scripts" / "methode" / "couverture-relecture.py"
    spec = importlib.util.spec_from_file_location("couverture_relecture", chemin)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


# L empreinte n a qu UNE definition, et c est celle du compteur. Deux copies, l une chez qui ecrit le
# manifeste et l autre chez qui le verifie, se seraient separees un jour sans que rien ne le dise :
# le cliquet aurait alors valide des entrees que le compteur n ecrit plus, ou l inverse. Le garde
# emprunte donc la definition plutot que de la redire (A2 : un garde dit ce qu il verifie).
_COMPTEUR = _compteur()
lignes_javadoc = _COMPTEUR.lignes_javadoc
empreinte = _COMPTEUR.empreinte


def manifeste(chemin: pathlib.Path = None) -> dict[str, str]:
    """Le manifeste des relus, lu par la fonction du compteur : un fichier, un lecteur."""
    return _COMPTEUR.relus(chemin or MANIFESTE)


def suspects(racine: pathlib.Path = None, table: dict[str, str] = None) -> list[str]:
    """Un suspect par fichier Java non relu, ou relu puis modifie.

    `racine` sert aux temoins, qui montent un arbre jetable. Sans elle, les deux racines Java du
    depot sont balayees, et les chemins rendus sont relatifs a la racine du depot - c est la cle du
    manifeste, et elle dit de quel arbre vient chaque fichier.
    """
    racines = [racine] if racine else list(_COMPTEUR.RACINES)
    base = racine or RACINE
    table = manifeste() if table is None else table
    trouves = []
    for f in sorted(f for r in racines for f in r.rglob("*.java")):
        rel = str(f.relative_to(base))
        connue = table.get(rel)
        if connue is None:
            trouves.append(f"{rel} : jamais relu")
        elif connue != empreinte(f):
            trouves.append(f"{rel} : javadoc modifiée depuis la relecture")
    return trouves


def _auto_test() -> int:
    import tempfile

    cas = []
    with tempfile.TemporaryDirectory() as d:
        r = pathlib.Path(d)
        (r / "A.java").write_text("/// Un contrat.\nclass A {}\n", encoding="utf-8")

        cas.append(("un fichier absent du manifeste est suspect", suspects(r, {}) == ["A.java : jamais relu"]))

        bonne = empreinte(r / "A.java")
        cas.append(("le meme fichier marque ne l est plus", suspects(r, {"A.java": bonne}) == []))

        # LE cas qui justifie l empreinte : sans elle, reecrire la javadoc d un fichier relu le
        # laisserait marque, et le manifeste affirmerait qu on a lu une prose qui n existe plus.
        (r / "A.java").write_text("/// Un contrat, reformule.\nclass A {}\n", encoding="utf-8")
        apres = suspects(r, {"A.java": bonne})
        cas.append(("une javadoc reecrite redevient suspecte", len(apres) == 1))
        cas.append(("et le suspect dit pourquoi", "modifiée depuis" in apres[0]))

        # L indentation appartient au formateur : une passe de spotless ne doit pas rouvrir la dette
        # de tout le depot. C est la seule variation que l empreinte ignore.
        (r / "A.java").write_text("    /// Un contrat, reformule.\n    class A {}\n", encoding="utf-8")
        cas.append(("mais un simple decalage ne la rouvre pas", suspects(r, {"A.java": empreinte(r / "A.java")}) == []))

        # Une etiquette de contrat compte : la relire est un geste, pas une formalite.
        (r / "B.java").write_text("/// Un contrat.\n/// @param x rien\nclass B {}\n", encoding="utf-8")
        sansTag = hashlib.sha256("Un contrat.".encode("utf-8")).hexdigest()[:12]
        cas.append(("une etiquette entre dans l empreinte", empreinte(r / "B.java") != sansTag))

        # Un fichier neuf arrive suspect sans qu on ait rien a declarer : c est la moitie du contrat.
        table = {"A.java": empreinte(r / "A.java")}
        cas.append(("un fichier neuf est suspect d office", suspects(r, table) == ["B.java : jamais relu"]))

        # Un manifeste vide ne doit pas rendre le depot vierge de dette.
        (r / "vide.txt").write_text("# rien\n", encoding="utf-8")
        cas.append(("un manifeste sans entree ne blanchit rien", len(suspects(r, manifeste(r / "vide.txt"))) == 2))

        # Une ligne mal formee est une affirmation illisible : elle se refuse, elle ne s ignore pas.
        # Le compteur et ce cliquet lisaient le manifeste chacun de son cote, et divergeaient
        # precisement ici - l un refusait, l autre passait outre. Ils n ont plus qu un lecteur.
        (r / "casse.txt").write_text("uneseulecolonne\n", encoding="utf-8")
        try:
            manifeste(r / "casse.txt")
            refuse = False
        except SystemExit:
            refuse = True
        cas.append(("une ligne de manifeste mal formee est refusee", refuse))

    for nom, ok in cas:
        print(f"  {'✔' if ok else '✘'} {nom}")
    rates = [n for n, ok in cas if not ok]
    if rates:
        print(f"\n{len(rates)} cas en échec : le cliquet ne tient pas ce qu'il annonce.", file=sys.stderr)
        return 1
    print(f"\n{len(cas)} cas : le cliquet voit un fichier jamais lu et une javadoc réécrite en douce.")
    return 0


if __name__ == "__main__":
    if "--auto-test" in sys.argv:
        sys.exit(_auto_test())
    listes = suspects()
    if "--releve" in sys.argv:
        for s in listes[:30]:
            print(f"  {s}")
        print(f"\n{len(listes)} fichiers de production dont la javadoc reste à relire")
        sys.exit(0)
    sys.exit(rapporte(ADR, "javadoc non relue, ou réécrite depuis sa relecture", listes, apercu=15))
