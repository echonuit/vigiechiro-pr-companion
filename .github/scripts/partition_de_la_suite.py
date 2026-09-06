#!/usr/bin/env python3
"""Repartit la suite de tests en lots, DERIVES de l arbre et non enumeres (#5329).

    python3 .github/scripts/partition_de_la_suite.py --lot 0 --sur 4 [--graine 2]
    python3 .github/scripts/partition_de_la_suite.py --verifie
    python3 .github/scripts/partition_de_la_suite.py --auto-test

## Pourquoi une partition, et ce qu elle coute

`ordre-alternatif` rejoue la suite en fork unique et ordre inverse, et c est avec ces deux
proprietes que les trois fuites d etat que `maven.yml` nomme se reproduisent. Il tient le chemin
critique a 20,4 min sur les demandes qui touchent du Java.

Le repartir rend **invisible une pollution entre deux classes tombees dans deux lots differents**.
Ce n est pas un effet de bord, c est une part de ce que le job mesure. L ADR du chantier #5294 le
nomme, et la GRAINE est ce qui limite la perte dans le temps : les paires co-localisees changent
d un passage a l autre, donc la couverture se reconstitue au fil des passages plutot que sur un.

## Elle se DERIVE, elle ne s enumere pas

L ADR 3450 a tranche pour ce job : « toute la suite, et non une liste de classes sensibles - une
liste ne voit que ce qu on y a mis et se perime. » Une partition tenue a la main serait cette liste.

Celle-ci part de l arbre : tout `*Test.java` sous `src/test/java`, ce qui est exactement ce que
surefire selectionne par defaut ici - les trois autres motifs (`Test*.java`, `*Tests.java`,
`*TestCase.java`) ne trouvent AUCUN fichier dans ce depot, mesure du 2026-09-06. Le corpus couvre
donc 100 % des classes par construction, et `--verifie` le prouve plutot que de l affirmer.

## Les noms sont PLEINEMENT QUALIFIES, et ce n est pas du zele

`TaxonDaoTest` existe dans deux paquets. Un `-Dtest=` par nom simple le ferait tomber dans deux lots
a la fois : l invariant « une classe, un lot » serait faux, et l union ne serait plus une partition.

## Le faux vert que `--verifie` empeche

#4544 : un passage tronque annoncait « toutes les classes de test » sur 618 des 758, sans un echec,
et rendait 0. Ici le meme defaut monte d un cran - quatre lots verts dont l union ne couvre pas la
suite. `--verifie` confronte donc l union au corpus **classe par classe**, et pour TOUTES les
graines : un compte egal ne prouve pas une couverture egale.
"""

from __future__ import annotations

import pathlib
import sys

RACINE = pathlib.Path(__file__).resolve().parents[2]
ARBRE = RACINE / "src" / "test" / "java"
LOTS = 4


def corpus(racine: pathlib.Path | None = None) -> list[str]:
    """Les classes de test, pleinement qualifiees, dans un ordre STABLE.

    Le tri est ce qui rend la partition reproductible : deux appels sur le meme arbre rendent la
    meme repartition, sans quoi deux lots pourraient jouer la meme classe et un troisieme aucune.
    """
    arbre = (racine or RACINE) / "src" / "test" / "java"
    if not arbre.is_dir():
        return []
    classes = []
    for f in arbre.rglob("*Test.java"):
        relatif = f.relative_to(arbre).with_suffix("")
        classes.append(".".join(relatif.parts))
    return sorted(classes)


def lot(
    numero: int, sur: int = LOTS, graine: int = 0, racine: pathlib.Path | None = None
) -> list[str]:
    """Le lot `numero` sur `sur`, decale par `graine`.

    La repartition est en ROND et non en blocs contigus : des blocs alphabetiques mettraient tout un
    paquet dans le meme lot, donc n eprouveraient jamais l ordre entre deux paquets voisins - qui est
    precisement la ou une fuite d etat se loge.
    """
    tout = corpus(racine)
    return [c for i, c in enumerate(tout) if (i + graine) % sur == numero]


def verifie(sur: int = LOTS, racine: pathlib.Path | None = None) -> int:
    """L union des lots EST le corpus, pour toutes les graines, et aucun lot n est vide."""
    tout = corpus(racine)
    ecarts: list[str] = []

    if not tout:
        print("❌ Corpus VIDE : aucun `*Test.java` sous `src/test/java`.")
        print("   Ce garde REFUSE de conclure plutot que d annoncer une partition de rien.")
        return 1

    for graine in range(sur):
        lots = [lot(n, sur, graine, racine) for n in range(sur)]
        vus: dict[str, int] = {}
        for n, contenu in enumerate(lots):
            if not contenu:
                ecarts.append(f"graine {graine} : le lot {n} est VIDE")
            for c in contenu:
                if c in vus:
                    ecarts.append(f"graine {graine} : `{c}` est dans les lots {vus[c]} ET {n}")
                vus[c] = n

        # Classe par classe, et non par un compte : deux erreurs qui se compensent rendraient le
        # meme total. C est la lecon de #4544, montee d un cran.
        for absente in sorted(set(tout) - set(vus)):
            ecarts.append(f"graine {graine} : `{absente}` n est dans AUCUN lot")
        for intruse in sorted(set(vus) - set(tout)):
            ecarts.append(f"graine {graine} : `{intruse}` est dans un lot sans etre au corpus")

        tailles = [len(c) for c in lots]
        if tailles and max(tailles) > 1.5 * (sum(tailles) / len(tailles)):
            ecarts.append(f"graine {graine} : lots desequilibres, tailles {tailles}")

    if ecarts:
        print(f"❌ {len(ecarts)} ecart(s) dans la partition de la suite :")
        for e in ecarts[:20]:
            print(f"   · {e}")
        if len(ecarts) > 20:
            print(f"   … et {len(ecarts) - 20} autre(s)")
        print()
        print("   Quatre lots verts dont l union ne couvre pas la suite est un FAUX VERT : chacun")
        print("   rend 0 en n ayant joue que sa part, et personne ne voit ce qui manque.")
        return 1

    print(
        f"✔ {len(tout)} classe(s) de test reparties en {sur} lots, sur {sur} graine(s) : "
        f"l union est le corpus, sans recouvrement ni lot vide."
    )
    return 0


def _auto_test() -> int:
    """Les cas eprouvent la PARTITION, et surtout ses bords : perte, doublon, corpus vide."""
    import shutil
    import tempfile

    echecs = 0
    with tempfile.TemporaryDirectory(prefix="vc-partition-") as bac:
        faux = pathlib.Path(bac) / "depot"
        arbre = faux / "src" / "test" / "java" / "fr" / "x"
        arbre.mkdir(parents=True)
        for n in range(10):
            (arbre / f"Cas{n}Test.java").write_text("// jouet\n", encoding="utf-8")

        # 1. Le controle NEGATIF : une partition saine passe. Une regle qui refuse tout est aussi
        #    inutile qu une regle qui accepte tout.
        if verifie(4, faux) == 0:
            print("  ✔ une partition saine reste verte")
        else:
            print("  ✘ une partition saine a ete refusee")
            echecs += 1

        # 2. L union EST le corpus, pour chaque graine.
        tout = set(corpus(faux))
        bon = all(
            set().union(*[set(lot(n, 4, g, faux)) for n in range(4)]) == tout for g in range(4)
        )
        print(f"  {'✔' if bon else '✘'} l'union des lots est le corpus, pour les quatre graines")
        echecs += 0 if bon else 1

        # 3. Aucun recouvrement.
        sans = all(sum(len(lot(n, 4, g, faux)) for n in range(4)) == len(tout) for g in range(4))
        print(f"  {'✔' if sans else '✘'} aucune classe n'est dans deux lots")
        echecs += 0 if sans else 1

        # 4. La graine CHANGE la repartition : sans cela, la rotation ne limiterait rien et l ADR
        #    reposerait sur une propriete que le code n a pas.
        bouge = lot(0, 4, 0, faux) != lot(0, 4, 1, faux)
        print(f"  {'✔' if bouge else '✘'} la graine déplace les classes d'un lot à l'autre")
        echecs += 0 if bouge else 1

        # 5. Un corpus VIDE fait REFUSER, jamais annoncer une partition de rien.
        vide = pathlib.Path(bac) / "vide"
        (vide / "src" / "test" / "java").mkdir(parents=True)
        if verifie(4, vide) == 1:
            print("  ✔ un corpus vide REFUSE de conclure")
        else:
            print("  ✘ un corpus vide n'a pas fait refuser")
            echecs += 1

        # 6. Une classe PERDUE est vue, et nommee. C est le defaut de #4544, monte d un cran.
        garde = lot
        try:
            globals()["lot"] = lambda n, sur=4, graine=0, racine=None: [
                c for c in garde(n, sur, graine, racine) if not c.endswith("Cas7Test")
            ]
            sortie = verifie(4, faux)
            if sortie == 1:
                print("  ✔ une classe absente de tous les lots est vue")
            else:
                print("  ✘ une classe perdue n'a pas ete vue")
                echecs += 1
        finally:
            globals()["lot"] = garde

        shutil.rmtree(faux, ignore_errors=True)

    print("\n6 cas de partition et de bord.")
    return 1 if echecs else 0


if __name__ == "__main__":
    if "--auto-test" in sys.argv:
        sys.exit(_auto_test())
    if "--verifie" in sys.argv:
        sys.exit(verifie())
    if "--lot" in sys.argv:
        i = sys.argv.index("--lot")
        numero = int(sys.argv[i + 1])
        sur = int(sys.argv[sys.argv.index("--sur") + 1]) if "--sur" in sys.argv else LOTS
        graine = int(sys.argv[sys.argv.index("--graine") + 1]) if "--graine" in sys.argv else 0
        classes = lot(numero, sur, graine)
        if not classes:
            print(
                f"lot {numero}/{sur} VIDE : refus plutot qu un `-Dtest=` qui ne selectionne rien.",
                file=sys.stderr,
            )
            sys.exit(1)
        print(",".join(classes))
        sys.exit(0)
    print("usage : --lot <n> [--sur N] [--graine G] | --verifie | --auto-test", file=sys.stderr)
    sys.exit(2)
