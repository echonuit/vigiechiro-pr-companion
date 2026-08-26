#!/usr/bin/env python3
"""Quelle javadoc a ete relue, quelle javadoc reste a lire - et ce qui a bouge depuis.

Le chantier A30 se mesurait par un cliquet de LONGUEUR : les blocs de plus de huit lignes de prose.
Cette mesure ne dit rien des blocs courts, qui peuvent etre tout aussi caducs, ni de ceux qu on n a
jamais ouverts. « Zero commentaire non relu » ne se prouve pas avec elle.

Ce compteur lit un manifeste - `relus.txt` - et rend ce qui reste. Un fichier n y entre que lorsque
TOUS ses blocs ont ete ouverts et juges, pas seulement le plus long.

**Chaque entree porte l empreinte de la javadoc relue.** Sans elle, un fichier marque relu resterait
marque apres qu on a reecrit ses commentaires : le manifeste dirait « lu » d une prose que personne
n a jamais lue. Avec elle, toute javadoc qui change fait ressortir son fichier dans les restants,
et il faut le relire pour le remarquer. C est ce qui rend « ne rien oublier » mecanique.

    --reste N       : les N fichiers non relus les plus lourds en prose, pour choisir la tranche.
    --marque F...   : porte des fichiers au manifeste, avec leur empreinte du jour.
    --verifie       : sortir 1 s il reste des fichiers non relus, ou une entree morte.
    --auto-test     : joue les temoins de ce compteur.
"""

import hashlib
import pathlib
import sys

RACINE = pathlib.Path(__file__).resolve().parents[2]
PRODUCTION = RACINE / "src" / "main" / "java"
TESTS = RACINE / "src" / "test" / "java"
# Les deux racines de Java du depot. Les chemins du manifeste sont relatifs a la RACINE, et non a
# l une d elles : une cle « fr/…/Machin.java » ne dirait pas de quel arbre elle vient, et un
# homonyme entre production et test se recouvrirait en silence.
RACINES = (PRODUCTION, TESTS)
MANIFESTE = pathlib.Path(__file__).parent / "relus.txt"

# Les suffixes du corpus. Un seul aujourd hui, nomme plutot qu ecrit en dur : la question « ce
# chemin appartient-il au corpus » se pose a trois endroits, et trois copies auraient derive.
SUFFIXE = ".java"

ENTETE = (
    "# Fichiers Java dont TOUS les blocs de javadoc ont ete ouverts et juges.\n"
    "# Une ligne par fichier : <empreinte>  <chemin relatif a la racine du depot>.\n"
    "# L empreinte est celle de la javadoc RELUE : si elle change, le fichier redevient a relire.\n"
    "# Voir couverture-relecture.py et scripts/adr/cliquet-javadoc-non-relue.py.\n"
)


def lignes_javadoc(fichier: pathlib.Path) -> list[str]:
    """Le contenu des lignes `///` du fichier, sans leur indentation ni leur prefixe."""
    retenues = []
    for ligne in fichier.read_text(encoding="utf-8").split("\n"):
        nu = ligne.strip()
        if nu.startswith("///"):
            retenues.append(nu[3:].strip())
    return retenues


def empreinte(fichier: pathlib.Path) -> str:
    """L empreinte de la javadoc d un fichier : ce qui change quand un commentaire change.

    Elle porte les etiquettes de contrat autant que la prose : reecrire un `@param` est une
    modification de javadoc, et la relire est le geste que ce manifeste enregistre. Elle ignore en
    revanche l indentation, qui appartient au formateur et non a l auteur.
    """
    corps = "\n".join(lignes_javadoc(fichier))
    return hashlib.sha256(corps.encode("utf-8")).hexdigest()[:12]


def prose(fichier: pathlib.Path) -> int:
    """Les lignes de prose javadoc du fichier, etiquettes de contrat exclues."""
    compte, dans_etiquette = 0, False
    for corps in lignes_javadoc(fichier):
        if not corps:
            dans_etiquette = False
            continue
        if corps.startswith("@") and len(corps) > 1 and corps[1].isalpha():
            dans_etiquette = True
            continue
        if dans_etiquette:
            continue
        compte += 1
    return compte


def relus(chemin: pathlib.Path = None) -> dict[str, str]:
    """Le manifeste : chemin -> empreinte de la javadoc au moment de la relecture.

    Seule lecture du manifeste : le cliquet qui le verifie emprunte cette fonction plutot que d en
    tenir une seconde. Les deux s etaient deja separees sur le cas de la ligne mal formee, l une la
    refusant et l autre l ignorant, pour un meme fichier.
    """
    chemin = chemin or MANIFESTE
    if not chemin.exists():
        return {}
    lu = {}
    for ligne in chemin.read_text(encoding="utf-8").split("\n"):
        nu = ligne.strip()
        if not nu or nu.startswith("#"):
            continue
        parts = nu.split(None, 1)
        # Une entree sans empreinte est illisible plutot qu absente : le manifeste porte une
        # affirmation, et une affirmation mal formee ne doit pas passer pour un silence.
        if len(parts) != 2:
            raise SystemExit(f"Entree de manifeste mal formee : « {nu} » (attendu : <empreinte>  <chemin>)")
        lu[parts[1]] = parts[0]
    return lu


def ecrire(manifeste: dict[str, str], chemin: pathlib.Path = None) -> None:
    corps = "".join(f"{manifeste[c]}  {c}\n" for c in sorted(manifeste))
    (chemin or MANIFESTE).write_text(ENTETE + corps, encoding="utf-8")


def racines(base: pathlib.Path = None) -> tuple[pathlib.Path, ...]:
    """Les deux arbres Java sous `base`. Le parametre sert aux temoins, qui montent un arbre jetable."""
    socle = base or RACINE
    return (socle / "src" / "main" / "java", socle / "src" / "test" / "java")


def corpus(base: pathlib.Path = None) -> list[pathlib.Path]:
    """Les fichiers que le manifeste a vocation a porter, et eux seuls."""
    return sorted(f for r in racines(base) if r.is_dir() for f in r.rglob(f"*{SUFFIXE}"))


def dans_le_corpus(absolu: pathlib.Path, base: pathlib.Path = None) -> bool:
    """Ce chemin est-il un fichier du corpus ? Fonction PURE, pour le temoin."""
    return absolu.suffix == SUFFIXE and any(absolu.is_relative_to(r) for r in racines(base))


def mortes(table: dict[str, str], base: pathlib.Path = None) -> list[str]:
    """Les entrees qui ne designent aucun fichier du corpus. Fonction PURE, pour le temoin.

    Deux causes, et les deux demandent la meme action - retirer la ligne. Le fichier a ete supprime
    ou deplace, et l entree ne garde plus rien ; ou l entree n a jamais rien garde, un chemin hors
    corpus ayant ete marque. La seconde ne devrait plus arriver depuis que [#marque] la refuse, mais
    le manifeste est un fichier que l on edite aussi a la main.
    """
    socle = base or RACINE
    vivants = {str(f.relative_to(socle)) for f in corpus(socle)}
    return sorted(c for c in table if c not in vivants)


def etat(base: pathlib.Path = None, source: pathlib.Path = None) -> tuple[list, list, list]:
    """(relus, restants, entrees mortes). Un restant dit POURQUOI il l est."""
    socle = base or RACINE
    deja = relus(source)
    lus, reste = [], []
    for f in corpus(socle):
        rel = str(f.relative_to(socle))
        connue = deja.get(rel)
        if connue is None:
            reste.append((prose(f), rel, "jamais relu"))
        elif connue != empreinte(f):
            reste.append((prose(f), rel, "javadoc modifiée depuis la relecture"))
        else:
            lus.append((prose(f), rel))
    lus.sort(reverse=True)
    reste.sort(reverse=True)
    return lus, reste, mortes(deja, socle)


def marque(chemins: list[str], base: pathlib.Path = None, source: pathlib.Path = None) -> int:
    """Porte des fichiers au manifeste avec leur empreinte du jour.

    **Un chemin hors corpus est refuse** (#4527). Une entree posee ailleurs n est jamais relue,
    jamais perimee, jamais signalee : `etat` ne parcourt que les deux arbres Java, donc rien ne peut
    la voir. Elle grossit en revanche le total que cette commande annonce, qui cesse alors d etre
    celui du corpus - et deux chiffres qui devraient dire la meme chose se mettent a diverger.
    """
    socle = base or RACINE
    fichier = source or MANIFESTE
    manifeste = relus(fichier)
    for brut in chemins:
        chemin = pathlib.Path(brut)
        absolu = (chemin if chemin.is_absolute() else socle / chemin).resolve()
        if not absolu.is_file():
            raise SystemExit(f"Fichier introuvable : {brut}")
        if not dans_le_corpus(absolu, socle):
            raise SystemExit(
                f"Hors corpus : {brut}\n"
                f"  Le manifeste ne porte que des {SUFFIXE} de src/main/java et src/test/java.\n"
                "  Ailleurs, l entree ne serait ni relue ni perimee ni signalee, et elle fausserait"
                " le total."
            )
        manifeste[str(absolu.relative_to(socle))] = empreinte(absolu)
    ecrire(manifeste, fichier)
    return len(manifeste)


def _auto_test() -> int:
    """Les temoins de ce compteur : ce qu il refuse, et ce qu il signale.

    Chaque cas monte un arbre jetable plutot que de lire le depot : un temoin qui depend du corpus
    reel devient vert le jour ou le corpus change, sans que personne l ait decide.
    """
    import tempfile

    cas = []
    with tempfile.TemporaryDirectory() as d:
        r = pathlib.Path(d)
        production = r / "src" / "main" / "java"
        production.mkdir(parents=True)
        (production / "A.java").write_text("/// Un contrat.\nclass A {}\n", encoding="utf-8")
        (r / "note.md").write_text("de la prose\n", encoding="utf-8")
        registre = r / "relus.txt"

        marque(["src/main/java/A.java"], r, registre)
        cas.append(("un .java du corpus entre au manifeste", list(relus(registre)) == ["src/main/java/A.java"]))

        # LE cas de #4527. Sans ce refus, la ligne entrait, aucun parcours ne la voyait, et le total
        # annonce cessait d etre celui du corpus.
        try:
            marque(["note.md"], r, registre)
            refuse = False
        except SystemExit:
            refuse = True
        cas.append(("un fichier hors corpus est refuse", refuse))
        cas.append(("et il n a rien laisse derriere lui", list(relus(registre)) == ["src/main/java/A.java"]))

        # Le refus porte sur l APPARTENANCE, pas sur le suffixe seul : un `.java` range ailleurs
        # serait tout aussi invisible.
        (r / "B.java").write_text("/// Ailleurs.\nclass B {}\n", encoding="utf-8")
        try:
            marque(["B.java"], r, registre)
            refuse_hors_arbre = False
        except SystemExit:
            refuse_hors_arbre = True
        cas.append(("un .java hors des deux arbres est refuse", refuse_hors_arbre))

        # Un chemin qui n existe pas se refuse toujours, et pas pour la meme raison.
        try:
            marque(["src/main/java/Absent.java"], r, registre)
            refuse_absent = False
        except SystemExit:
            refuse_absent = True
        cas.append(("un fichier introuvable est refuse", refuse_absent))

        lus, reste, perdues = etat(r, registre)
        cas.append(("le fichier marque est compte relu", [c for _, c in lus] == ["src/main/java/A.java"]))
        cas.append(("et rien n est mort", perdues == []))

        # L autre moitie : une entree qui ne designe plus rien. Elle arrive par une suppression, ou
        # par une edition a la main du manifeste - le refus ci-dessus ne ferme que la porte d entree.
        (production / "A.java").unlink()
        lus, reste, perdues = etat(r, registre)
        cas.append(("un fichier supprime laisse une entree morte", perdues == ["src/main/java/A.java"]))
        cas.append(("et il ne compte plus parmi les relus", lus == []))

        # Controle NEGATIF : un manifeste vide sur un corpus vide ne signale rien.
        vide = r / "vide.txt"
        vide.write_text("# rien\n", encoding="utf-8")
        cas.append(("un manifeste vide ne rend aucune morte", etat(r, vide)[2] == []))

    for nom, ok in cas:
        print(f"  {'✔' if ok else '✘'} {nom}")
    rates = [n for n, ok in cas if not ok]
    if rates:
        print(f"\n{len(rates)} cas en échec : le compteur ne tient pas ce qu'il annonce.", file=sys.stderr)
        return 1
    print(f"\n{len(cas)} cas : le manifeste refuse ce qu'il ne saurait garder, et signale ce qu'il a perdu.")
    return 0


def main() -> int:
    if "--auto-test" in sys.argv:
        return _auto_test()
    if "--marque" in sys.argv:
        i = sys.argv.index("--marque")
        print(f"{marque(sys.argv[i + 1:])} fichiers au manifeste")
        return 0

    lus, reste, perdues = etat()
    total = len(lus) + len(reste)
    prose_lue = sum(p for p, _ in lus)
    prose_reste = sum(p for p, _, _ in reste)
    if "--reste" in sys.argv:
        i = sys.argv.index("--reste")
        n = int(sys.argv[i + 1]) if i + 1 < len(sys.argv) else 20
        for p, f, motif in reste[:n]:
            suffixe = "" if motif == "jamais relu" else f"  ({motif})"
            print(f"  {p:4}  {f}{suffixe}")
        print()
    part = 100 * len(lus) / total if total else 100
    print(
        f"relus : {len(lus)}/{total} fichiers ({part:.1f} %), "
        f"{prose_lue} lignes de prose lues, {prose_reste} restantes"
    )
    modifies = [f for _, f, m in reste if m != "jamais relu"]
    if modifies:
        print(f"dont {len(modifies)} fichier(s) dont la javadoc a changé depuis leur relecture :")
        for f in modifies[:10]:
            print(f"  {f}")
    if perdues:
        print(f"\n{len(perdues)} entrée(s) du manifeste ne désignent aucun fichier du corpus :")
        for c in perdues[:10]:
            print(f"  {c}")
        print("  Retirez la ligne : elle ne garde rien, et elle compte dans le total.")
    if "--verifie" in sys.argv and reste:
        print(f"{len(reste)} fichier(s) dont la javadoc n'a pas été relue.", file=sys.stderr)
        return 1
    if "--verifie" in sys.argv and perdues:
        print(f"{len(perdues)} entrée(s) morte(s) au manifeste.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
