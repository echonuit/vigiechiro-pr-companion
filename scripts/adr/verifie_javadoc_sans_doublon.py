#!/usr/bin/env python3
"""Deux lignes de javadoc identiques et consecutives sont une coupe ratee, jamais une intention.

Le defaut est arrive quinze fois d un coup, et il a survecu a la compilation, a spotless et aux
tests : un outil de coupe posait son texte a `depart` au lieu de `depart - 1`, si bien que la
premiere ligne de l ancien bloc restait devant la nouvelle. Le lecteur voyait la meme phrase deux
fois de suite ; aucun garde ne la voyait.

Le defaut a une seconde forme, trouvee en lisant : la ligne d avant n est pas identique, elle est le
DEBUT TRONQUE de la suivante. `TraiterPassages` ouvrait sur « applique une action a plusieurs
passages, avec », puis sur la meme phrase complete. Le controle exact ne la voyait pas.

Une TROISIEME forme echappe a l adjacence : deux BLOCS entiers identiques dans un meme fichier,
poses sur deux membres differents. C est ce que laisse une doc recopiee d un membre a son voisin -
`RapprochementSites` documentait son champ `liens` avec la phrase de sa constante `LIBELLE_SITES`.
Aucune des deux lignes ne touche l autre, et le controle d adjacence ne pouvait pas les voir.

Une QUATRIEME forme, trouvee en lisant elle aussi : deux lignes qui partagent un long DEBUT puis
divergent. C est ce que laisse une premiere ligne REECRITE dont l ancienne survit - `ExportVuCsv`
ouvrait deux fois sur « Écrit un CSV `_Vu` reinjectable sur le portail Vigie-Chiro ( », puis
divergeait ; ni l egalite ni le prefixe strict ne la voyaient.

Le controle est **deterministe**, sur quatre formes : deux lignes `///` qui se suivent et dont la
seconde repete la premiere, a l identique ou en la prolongeant ou en la tronquant ; deux lignes qui
ouvrent a l identique sur quarante caracteres puis divergent ; et deux blocs entiers identiques dans
un meme fichier. Une ligne `///` vide ne compte pas - elle aere, elle se
repete legitimement - et le prefixe doit faire au moins 30 caracteres, pour qu un debut de
phrase commun a deux lignes ne compte pas par hasard.

Deux membres qu une documentation identique ne distingue pas sont deux membres que le lecteur ne
distingue pas : la reponse est de dire ce qui les separe, jamais de lever le controle.

Exit 0 si aucun doublon, 1 sinon.
"""

import argparse
import pathlib
import sys
import tempfile

RACINE = pathlib.Path(__file__).resolve().parents[2]
JAVA = RACINE / "src"


# En deca, un debut de phrase commun a deux lignes voisines peut etre une coincidence.
PLANCHER = 30

# Le plancher du PREFIXE COMMUN est plus haut : deux lignes qui divergent apres quarante caracteres
# ne se repetent pas par hasard, la ou trente laissaient passer des listes a puces et des exemples.
# Mesure sur le corpus : a 40, deux couples, tous deux des blocs recolles ; a 30, trois faux positifs.
PLANCHER_PREFIXE = 40

# Ce qui n est pas de la prose : une puce, un tableau, du code, une etiquette. Deux de ces lignes
# partagent legitimement un long debut - « - `apercu-import-...` » deux fois de suite.
MARQUEUR = ("- ", "* ", "|", "`", ">", "#", "@")


def doublons(racine: pathlib.Path = None) -> list[str]:
    """Les couples de lignes `///` dont la seconde repete la premiere, entiere ou prolongee."""
    racine = racine or JAVA
    trouves = []
    for f in sorted(racine.rglob("*.java")):
        lignes = f.read_text(encoding="utf-8").split("\n")
        fence = False
        for i in range(1, len(lignes)):
            avant, apres = lignes[i - 1].strip(), lignes[i].strip()
            if not (avant.startswith("///") and apres.startswith("///")):
                continue
            corps, suite = avant[3:].strip(), apres[3:].strip()
            if corps.startswith("```"):
                fence = not fence
            if not corps:
                continue
            dansUnBloc = fence or any(corps.startswith(m) or suite.startswith(m) for m in MARQUEUR)
            nom = f.relative_to(racine) if racine != JAVA else f.relative_to(RACINE)
            if corps == suite:
                trouves.append(f"{nom}:{i + 1} répète la ligne précédente : {corps[:70]}")
            elif len(corps) >= PLANCHER and suite.startswith(corps):
                trouves.append(f"{nom}:{i + 1} reprend la ligne précédente en la prolongeant : {corps[:70]}")
            # Le sens inverse : une ligne ENTIERE suivie de son propre debut. C'est la forme que
            # prend un bloc recolle sur lui-meme, ou la premiere ligne survit au repli qui l'a
            # reecrite. `NuitRecupereeDao` l'a portee sans que rien ne la voie.
            elif len(suite) >= PLANCHER and corps.startswith(suite):
                trouves.append(f"{nom}:{i + 1} reprend le début de la ligne précédente : {suite[:70]}")
            # La troisieme forme : deux lignes qui partagent un long DEBUT puis divergent. C est ce que
            # laisse une premiere ligne reecrite dont l ancienne survit - `ExportVuCsv` ouvrait deux fois
            # sur « Écrit un CSV `_Vu` réinjectable sur le portail Vigie-Chiro ( », puis divergeait.
            elif not dansUnBloc and (commun := prefixeCommun(corps, suite)) >= PLANCHER_PREFIXE:
                trouves.append(
                    f"{nom}:{i + 1} recommence la ligne précédente ({commun} caractères) : {corps[:60]}")
        trouves.extend(blocsJumeaux(f, racine))
    return trouves


def prefixeCommun(a: str, b: str) -> int:
    """Combien de caracteres ouvrent les deux lignes a l identique."""
    commun = 0
    for x, y in zip(a, b):
        if x != y:
            break
        commun += 1
    return commun


def blocsJumeaux(fichier: pathlib.Path, racine: pathlib.Path) -> list[str]:
    """Deux blocs `///` ENTIERS identiques dans un meme fichier, sur deux membres differents.

    L adjacence ne peut pas les voir : ils sont separes par du code. C est la forme que prend une doc
    recopiee d un membre a son voisin, et le voisin garde alors une phrase qui ne parle pas de lui.
    """
    lignes = fichier.read_text(encoding="utf-8").split("\n")
    nom = fichier.relative_to(racine) if racine != JAVA else fichier.relative_to(RACINE)
    vus, trouves, i = {}, [], 0
    while i < len(lignes):
        if lignes[i].strip().startswith("///"):
            j = i
            while j < len(lignes) and lignes[j].strip().startswith("///"):
                j += 1
            corps = tuple(l.strip()[3:].strip() for l in lignes[i:j])
            # Un bloc entierement vide ne dit rien : deux d entre eux ne se repetent pas.
            if any(corps):
                if corps in vus:
                    trouves.append(
                        f"{nom}:{i + 1} répète mot pour mot le bloc de la ligne {vus[corps]} : {corps[0][:60]}")
                else:
                    vus[corps] = i + 1
            i = j
        else:
            i += 1
    return trouves


def _auto_test() -> int:
    cas = []
    with tempfile.TemporaryDirectory() as d:
        r = pathlib.Path(d)

        (r / "A.java").write_text("/// Une phrase.\n/// Une autre.\nclass A {}\n", encoding="utf-8")
        cas.append(("deux lignes différentes passent", doublons(r) == []))

        (r / "A.java").write_text("/// Une phrase.\n/// Une phrase.\nclass A {}\n", encoding="utf-8")
        f = doublons(r)
        cas.append(("la même ligne deux fois rougit", len(f) == 1))
        cas.append(("le refus cite la ligne", any("Une phrase." in x for x in f)))

        # LE cas qui borne la portee : les lignes `///` vides separent des paragraphes et se
        # suivent legitimement. Sans cette exception, tout bloc aere serait un suspect.
        (r / "A.java").write_text("/// Un.\n///\n///\n/// Deux.\nclass A {}\n", encoding="utf-8")
        cas.append(("deux lignes vides ne sont pas un doublon", doublons(r) == []))

        # LA seconde forme : la ligne d avant est le DEBUT TRONQUE de la suivante. Une coupe
        # posee au mauvais endroit laisse le debut de l ancienne phrase devant la nouvelle, et le
        # controle exact ne la voit pas - trois blocs vivaient ainsi.
        (r / "A.java").write_text(
            "/// Applique une action a plusieurs passages, avec\n"
            "/// Applique une action a plusieurs passages, avec l ecran d eligibilite.\n"
            "class A {}\n",
            encoding="utf-8",
        )
        f = doublons(r)
        cas.append(("un début tronqué de la ligne suivante rougit", len(f) == 1))
        cas.append(("le refus dit qu'il s'agit d'un prolongement",
                    any("prolongeant" in x for x in f)))

        # Et la borne : deux lignes voisines peuvent partager un debut court sans que ce soit le
        # defaut. Sans plancher, « Le point » suivi de « Le point est » compterait.
        (r / "A.java").write_text("/// Le point\n/// Le point est libre.\nclass A {}\n", encoding="utf-8")
        cas.append(("un début commun trop court ne compte pas", doublons(r) == []))

        # Deux phrases identiques SEPAREES ne sont pas le defaut vise : le defaut est la coupe
        # ratee, qui laisse la ligne juste avant sa remplacante.
        (r / "A.java").write_text("/// Un.\n/// Deux.\n/// Un.\nclass A {}\n", encoding="utf-8")
        cas.append(("la même phrase plus loin passe", doublons(r) == []))

        # Du code Java repete n est pas notre affaire : ce garde ne lit que la javadoc.
        (r / "A.java").write_text("class A {\n    int x = 1;\n    int x = 1;\n}\n", encoding="utf-8")
        cas.append(("le code répété ne le concerne pas", doublons(r) == []))

        (r / "A.java").unlink()
        cas.append(("sans source, le garde ne prétend rien", doublons(r) == []))

        # Le SENS INVERSE : une ligne entiere suivie de son propre debut. C'est la forme que prend
        # un bloc recolle sur lui-meme - la premiere ligne survit au repli qui l'a reecrite - et le
        # garde ne la voyait pas : il ne regardait que la ligne qui PROLONGE sa voisine.
        (r / "A.java").write_text(
            "/// Reconnaît une nuit récupérée : rapatriée avec ses observations\n"
            "/// Reconnaît une nuit récupérée : rapatriée avec ses\n"
            "/// observations et son rattachement.\nclass A {}\n",
            encoding="utf-8")
        f = doublons(r)
        cas.append(("une ligne suivie de son propre début rougit", len(f) == 1))
        cas.append(("et le refus dit laquelle", any("reprend le début" in x for x in f)))

        # Le meme plancher borne les deux sens : un debut commun court reste une coincidence.
        (r / "A.java").write_text("/// Un mot de plus.\n/// Un mot.\nclass A {}\n", encoding="utf-8")
        cas.append(("un début commun trop court passe", doublons(r) == []))

        # LA TROISIEME forme : deux blocs entiers identiques, separes par du code. L adjacence ne
        # peut pas les voir. `RapprochementSites` documentait son champ avec la phrase de sa constante.
        (r / "A.java").write_text(
            "/// Libellé du compte-rendu (pluriel).\n"
            "static final String L = \"sites\";\n\n"
            "/// Libellé du compte-rendu (pluriel).\n"
            "private final Dao liens;\n",
            encoding="utf-8")
        f = doublons(r)
        cas.append(("deux blocs jumeaux rougissent", len(f) == 1))
        cas.append(("et le refus nomme les deux lignes", any("répète mot pour mot le bloc" in x for x in f)))

        # Deux blocs VOISINS mais differents ne se repetent pas : le controle porte sur le contenu.
        (r / "A.java").write_text(
            "/// Le libellé du compte-rendu.\nstatic final String L = \"sites\";\n\n"
            "/// Les liens vers la plateforme.\nprivate final Dao liens;\n",
            encoding="utf-8")
        cas.append(("deux blocs différents passent", doublons(r) == []))

        # Deux blocs faits de lignes `///` VIDES ne disent rien : ils ne se repetent pas.
        (r / "A.java").write_text("///\nclass A {}\n\n///\nclass B {}\n", encoding="utf-8")
        cas.append(("deux blocs vides ne se répètent pas", doublons(r) == []))

        # LA TROISIEME forme : deux lignes qui partagent un long DEBUT puis divergent. C est ce que
        # laisse une premiere ligne reecrite dont l ancienne survit ; ni l egalite ni le prefixe strict
        # ne la voient. `ExportVuCsv` et `Observation` la portaient toutes deux.
        (r / "A.java").write_text(
            "/// Écrit un CSV réinjectable sur le portail Vigie-Chiro (parcours P7, étape E7 ; règles\n"
            "/// Écrit un CSV réinjectable sur le portail Vigie-Chiro (R17, R24). Symétrique du parseur.\n"
            "class A {}\n",
            encoding="utf-8")
        f = doublons(r)
        cas.append(("deux lignes au même long début rougissent", len(f) == 1))
        cas.append(("et le refus chiffre le début commun", any("recommence la ligne précédente" in x for x in f)))

        # Le plancher borne la regle : deux phrases qui ouvrent pareil sur trente caracteres arrivent.
        (r / "A.java").write_text(
            "/// Le fichier de résultats est lu par le parseur.\n"
            "/// Le fichier de résultats est écrit par l'export.\nclass A {}\n",
            encoding="utf-8")
        cas.append(("un début commun sous le plancher passe", doublons(r) == []))

        # ET l exception qui rend la regle tenable : deux PUCES d une meme liste ouvrent legitimement
        # pareil. Sans elle, toute enumeration de fichiers voisins serait un suspect.
        (r / "A.java").write_text(
            "/// - `apercu-import-participation-une-nuit.png` : la forme singulière de la mention.\n"
            "/// - `apercu-import-participation-trois-nuits.png` : la forme plurielle, la plus longue.\n"
            "class A {}\n",
            encoding="utf-8")
        cas.append(("deux puces d'une même liste passent", doublons(r) == []))

        # Idem dans un bloc de code : deux lignes d un exemple se ressemblent par construction.
        (r / "A.java").write_text(
            "/// ```\n"
            "/// JeuDeDonnees.dans(source).point(\"A1\").nuit(1, 2026).importee();\n"
            "/// JeuDeDonnees.dans(source).point(\"A1\").nuit(2, 2026).deposee();\n"
            "/// ```\nclass A {}\n",
            encoding="utf-8")
        cas.append(("deux lignes d'un bloc de code passent", doublons(r) == []))

    for nom, ok in cas:
        print(f"  {'✔' if ok else '✘'} {nom}")
    rates = [n for n, ok in cas if not ok]
    if rates:
        print(f"\n{len(rates)} cas en échec : le garde ne dit pas ce qu'il vérifie.", file=sys.stderr)
        return 1
    print(f"\n{len(cas)} cas : le garde voit une ligne répétée et laisse passer le reste.")
    return 0


def main() -> int:
    p = argparse.ArgumentParser(description="Aucune ligne de javadoc répétée deux fois de suite")
    p.add_argument("--auto-test", action="store_true", help="éprouve le refus sur des fixtures")
    args = p.parse_args()
    if args.auto_test:
        return _auto_test()
    trouves = doublons()
    for t in trouves:
        print(f"  {t}", file=sys.stderr)
    if trouves:
        print(f"\n{len(trouves)} ligne(s) de javadoc répétée(s).", file=sys.stderr)
        return 1
    print("Javadoc : aucune ligne répétée deux fois de suite.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
