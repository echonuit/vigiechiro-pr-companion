#!/usr/bin/env python3
"""Le contrat de chaque garde, pour comparer deux arbres autrement que par des noms (issue #4636).

L inventaire du portage comparait des NOMS de fichiers, apres normalisation de deux conventions :
la ligne d origine nomme `cliquet-<geste>.py`, ce depot `<numero>-<geste>.py`. Il rendait donc
« present » pour tout homonyme, et #4635 a montre ce qu il ne voyait pas : une loupe portee dont la
ligne de verdict parlait encore la langue de son depot d origine, comptee comme presente et ne
rapportant rien a personne.

**La difference que l inventaire normalisait est celle qui cassait le produit.**

Ce releve lit donc ce qu un garde DECLARE, et non comment son fichier s appelle :

| ce qui est lu | ou |
|---|---|
| le geste | le nom du fichier, prefixes retires |
| l ADR | la constante `ADR` du script |
| la population | ce qu il importe de `_commun`, ou les chemins qu il ecrit en clair |
| le seuil | le champ `ratchet:` ou `floor:` de l en-tete de son ADR |
| le verdict | le titre passe a l aide qui le rend |

**Ce qu il ne lit PAS, et c est dit.** Le PREDICAT, c est-a-dire ce que le garde refuse vraiment. Un
motif d expression reguliere ne se compare pas a un autre, et pretendre le faire rendrait un verdict
que rien ne fonde. Le releve met les deux titres cote a cote et s arrete la : un audit qui ne peut
pas trancher montre sans juger (article A13).

**L appariement se fait par le GESTE**, et c est sa seconde limite. Deux gardes qui traitent le
meme sujet sous deux gestes differents ne s apparient pas : `apostrophe-en-libelle` ici et
`apostrophe_droite` ailleurs sortent en DEUX absences, la ou un lecteur voit un ecart de
population. Le releve les montre toutes les deux et laisse le rapprochement a qui lit ; nommer
automatiquement deux gestes comme un seul demanderait un dictionnaire ecrit a la main, soit
exactement la liste qui derive et que ce releve existe pour remplacer.

**Il ne tourne pas en CI**, et c est delibere : il lit DEUX arbres, et l autre n existe pas sur le
runner. Il se lance a la main a l ouverture d un lot de portage.

Usage :
    python3 scripts/methode/contrats-des-gardes.py                 # cet arbre
    python3 scripts/methode/contrats-des-gardes.py <autre-arbre>   # les ecarts
    python3 scripts/methode/contrats-des-gardes.py --auto-test
"""

import pathlib
import re
import sys

RACINE = pathlib.Path(__file__).resolve().parents[2]

# Les prefixes que les deux conventions posent devant le geste.
PREFIXES = re.compile(r"^(cliquet-|loupe-|verifie[-_])?(\d{4}-)?(.*?)\.py$")

CONSTANTE_ADR = re.compile(r"^ADR(?:_[A-Z]+)? = \"([^\"]+)\"", re.M)
IMPORTE = re.compile(r"^from _commun import (.+?)(?:  #.*)?$", re.M)
CHEMIN_ECRIT = re.compile(r"\"(src/(?:main|test)/java[^\"]*)\"")

# L appel qui rend le verdict porte les DEUX choses qu on cherche : l ADR et le titre. Le premier
# argument est tantot une constante, tantot le numero en clair - les gardes les plus anciens
# ecrivent `rapporte("0008", ...)`. Lire la seule constante en manquait la moitie.
# Le premier argument identifie l ADR, et les deux arbres ne l ecrivent pas pareil : un NUMERO
# ici, un SLUG dans la ligne d origine, l un comme l autre parfois range dans une constante. Un
# releve qui ne saurait lire qu une des deux conventions ne comparerait rien.
VERDICT = re.compile(
    r"(?:rapporte|rapporte_plancher|loupe)\("
    r"\s*(?:\"([^\"]+)\"|([A-Za-z_][A-Za-z_0-9]*))\s*,\s*\"([^\"]{0,80})",
    re.S,
)

# Ni un garde, ni portable : le rapport, le resserreur, et les deux harnais qui EPROUVENT les gardes.
HORS_GARDES = {
    "rapport.py",
    "resserre_cliquets.py",
    "verifie_scripts.py",
    "verifie_temoins_non_decoratifs.py",
}

# Les listes d EXEMPTION, sous les quatre noms que ce depot leur donne. C est ce qu un portage
# ecrase sans qu aucun diff paraisse fautif (#4662) : le fichier importe est correct, son diff est
# propre, et il retire en silence ce que ce depot avait du ajouter pour son propre contexte.
#
# Mesure du 2026-08-28 : le mecanisme ne s est JAMAIS produit ici - les seize retraits que l histoire
# porte sont tous des elargissements annonces dans leur sujet. Il a failli, sur le garde de
# l apostrophe : le porter tel quel aurait efface trois exemptions nommant des SVG propres a ce
# depot, et fait rougir le garde sur au moins 22 occurrences dans des fichiers engendres.
EXEMPTIONS = re.compile(
    r"^(?:HORS_CHAMP|HORS_COUVERTURE|EXEMPT[A-Z_]*|RESERVES)\s*=\s*[\{\(]", re.M
)
CLE_EXEMPTEE = re.compile(r"^\s*\"([^\"]+)\"\s*[:,]", re.M)

CORPUS = ("RACINES_ANCREES", "RACINES", "PRODUCTION_ANCREE", "PRODUCTION", "TESTS_ANCRES", "TESTS")


def geste(nom: str) -> str:
    """Le geste que le fichier nomme, ses prefixes et son separateur normalises.

    Le SEPARATEUR compte autant que le prefixe : `verifie_apostrophe_droite.py` et
    `4368-apostrophe-droite.py` nomment le meme geste, et les apparier par le nom brut les rendait
    en DEUX absences la ou il n y a qu une convention de plus. Mesure du 2026-08-28, en renommant
    le garde de l apostrophe (#4637).
    """
    trouve = PREFIXES.match(nom)
    brut = trouve.group(3) if trouve else nom[:-3]
    return brut.replace("_", "-")


def sans_docstring(texte: str) -> str:
    """Le module prive de sa docstring, qui CITE des chemins sans en declarer aucun."""
    for marque in (chr(34) * 3, chr(39) * 3):
        debut = texte.find(marque)
        if debut < 0:
            continue
        fin = texte.find(marque, debut + 3)
        if fin > 0:
            return texte[:debut] + texte[fin + 3 :]
    return texte


def population(texte: str) -> str:
    """Ce que le garde lit, qu il l importe ou qu il l ecrive."""
    importe = IMPORTE.search(texte)
    if importe:
        noms = [n.strip() for n in importe.group(1).split(",")]
        corpus = [n for n in noms if n in CORPUS]
        if corpus:
            return " + ".join(sorted(corpus))
    # Un corpus se declare au niveau du MODULE ; ce qui est indente batit une fixture. La regle
    # vient de l ADR 4586, et sans elle le releve comptait les arbres temoins des auto-tests.
    #
    # La docstring de module est a la marge zero elle aussi, et elle CITE des chemins pour
    # expliquer la regle : `verifie_corpus_declare.py` en porte deux dans la sienne. Elle est
    # donc retiree avant lecture, sinon le releve prend une explication pour une declaration.
    modules = "\n".join(l for l in sans_docstring(texte).split("\n") if l[:1] not in (" ", "\t"))
    ecrits = sorted(set(CHEMIN_ECRIT.findall(modules)))
    return " + ".join(ecrits) if ecrits else "(non declaree)"


def numero_adr(texte: str) -> str | None:
    """Le numero de l ADR, lu dans l appel de verdict puis, a defaut, dans la constante."""
    appel = VERDICT.search(texte)
    if appel:
        if appel.group(1):
            return appel.group(1)
        constante = re.search(r"^%s = \"([^\"]+)\"" % re.escape(appel.group(2)), texte, re.M)
        if constante:
            return constante.group(1)
    constante = CONSTANTE_ADR.search(texte)
    return constante.group(1) if constante else None


def seuil(texte: str, decisions: pathlib.Path) -> str:
    """Le seuil declare par l ADR du garde, cliquet ou plancher."""
    numero = numero_adr(texte)
    if not numero:
        return "(pas d ADR declaree)"
    for fichier in sorted(decisions.glob("*.md")):
        # Un numero prefixe le nom du fichier ; un slug EST le nom du fichier.
        if not (fichier.name.startswith(numero + "-") or fichier.stem == numero):
            continue
        entete = fichier.read_text(encoding="utf-8").split("\n---\n")[0]
        for champ, mot in (("ratchet:", "cliquet"), ("floor:", "plancher")):
            marque = re.search(r"^%s\s*(\d+)\s*$" % champ, entete, re.M)
            if marque:
                return "%s %s" % (mot, marque.group(1))
        return "(sans seuil)"
    return "(ADR %s introuvable)" % numero


def exemptions(texte: str) -> str:
    """Ce que le garde s interdit de lire, et que ce depot a du lui ajouter.

    Le NOMBRE et non la liste : deux arbres n exemptent pas les memes fichiers, et confronter des
    chemins qui n existent que d un cote ne dirait rien. Un ecart de compte, lui, se lit.
    """
    debut = EXEMPTIONS.search(texte)
    if not debut:
        return "0"
    reste = texte[debut.end() :]
    fin = reste.find("\n}")
    if fin < 0:
        fin = reste.find("\n)")
    return str(len(CLE_EXEMPTEE.findall(reste[: fin if fin > 0 else 400])))


def contrats(racine: pathlib.Path) -> dict:
    """Le contrat de chaque garde d un arbre, indexe par son geste."""
    dossier = racine / "scripts" / "adr"
    decisions = racine / "dev-docs" / "decisions"
    out = {}
    for source in sorted(dossier.glob("*.py")):
        if source.name.startswith("_") or source.name in HORS_GARDES:
            continue
        texte = source.read_text(encoding="utf-8")
        titre = VERDICT.search(texte)
        out[geste(source.name)] = {
            "fichier": source.name,
            "population": population(texte),
            "seuil": seuil(texte, decisions),
            "exemptions": exemptions(texte),
            "verdict": (titre.group(3).strip() if titre else "(aucun verdict rendu)"),
        }
    return out


def ecarts(ici: dict, ailleurs: dict) -> list[str]:
    """Les differences entre deux arbres, appariees par le GESTE et non par le nom du fichier."""
    lignes = []
    for g in sorted(set(ici) | set(ailleurs)):
        a, b = ici.get(g), ailleurs.get(g)
        if a is None:
            lignes.append("%-38s ABSENT ici, present ailleurs (%s)" % (g, b["fichier"]))
            continue
        if b is None:
            lignes.append("%-38s present ici seulement (%s)" % (g, a["fichier"]))
            continue
        for champ in ("population", "seuil", "exemptions", "verdict"):
            if a[champ] != b[champ]:
                lignes.append(
                    "%-38s %-11s ici « %s » / ailleurs « %s »" % (g, champ, a[champ], b[champ])
                )
    return lignes


def _auto_test() -> int:
    """Le releve retrouve les deux ecarts connus, sinon il ne mesure pas ce qu il annonce."""
    import tempfile

    echecs = 0

    def verifie(libelle, obtenu, attendu):
        nonlocal echecs
        if obtenu == attendu:
            print("  ✔ %s" % libelle)
        else:
            print("  ✘ %s : attendu %s, obtenu %s" % (libelle, attendu, obtenu))
            echecs = 1

    verifie(
        "le geste se lit sous les deux conventions",
        geste("cliquet-echec-silencieux.py") == geste("0008-echec-silencieux.py"),
        True,
    )
    verifie(
        "le separateur ne separe pas deux gestes identiques",
        geste("verifie_apostrophe_droite.py") == geste("4368-apostrophe-droite.py"),
        True,
    )
    verifie(
        "un identifiant en SLUG se lit comme un numero",
        numero_adr('rapporte("aucun-echec-silencieux", "titre", [])'),
        "aucun-echec-silencieux",
    )
    verifie(
        "le geste d une loupe aussi",
        geste("loupe-densite-de-commentaire.py") == geste("loupe-4472-densite-de-commentaire.py"),
        True,
    )

    with tempfile.TemporaryDirectory() as brut:
        r = pathlib.Path(brut)
        for arbre in ("a", "b"):
            (r / arbre / "scripts" / "adr").mkdir(parents=True)
            (r / arbre / "dev-docs" / "decisions").mkdir(parents=True)

        # L ecart de POPULATION : le meme geste, deux corpus. C est celui de #4637.
        (r / "a/scripts/adr/verifie_apostrophe.py").write_text(
            'from _commun import PRODUCTION, rapporte\nADR = "1"\nrapporte(ADR, "apostrophe", [])\n',
            encoding="utf-8",
        )
        (r / "b/scripts/adr/cliquet-apostrophe.py").write_text(
            'from _commun import RACINES, rapporte\nADR = "1"\nrapporte(ADR, "apostrophe", [])\n',
            encoding="utf-8",
        )
        trouves = ecarts(contrats(r / "a"), contrats(r / "b"))
        verifie("un ecart de population est vu", any("population" in l for l in trouves), True)

        # L ecart de VERDICT : celui de #4635, avant sa correction.
        (r / "a/scripts/adr/verifie_apostrophe.py").write_text(
            'from _commun import RACINES, rapporte\nADR = "1"\nrapporte(ADR, "densite ici", [])\n',
            encoding="utf-8",
        )
        (r / "b/scripts/adr/cliquet-apostrophe.py").write_text(
            'from _commun import RACINES, rapporte\nADR = "1"\nrapporte(ADR, "densite ailleurs", [])\n',
            encoding="utf-8",
        )
        trouves = ecarts(contrats(r / "a"), contrats(r / "b"))
        verifie("un ecart de verdict est vu", any("verdict" in l for l in trouves), True)

        # L ecart d EXEMPTIONS : ce qu un portage efface sans que le diff paraisse fautif.
        (r / "a/scripts/adr/verifie_apostrophe.py").write_text(
            'from _commun import RACINES, rapporte\nADR = "1"\n'
            'HORS_CHAMP = {\n    "un.svg": "engendre",\n    "deux.svg": "engendre",\n}\n'
            'rapporte(ADR, "densite ici", [])\n',
            encoding="utf-8",
        )
        (r / "b/scripts/adr/cliquet-apostrophe.py").write_text(
            'from _commun import RACINES, rapporte\nADR = "1"\nrapporte(ADR, "densite ici", [])\n',
            encoding="utf-8",
        )
        trouves = ecarts(contrats(r / "a"), contrats(r / "b"))
        verifie("un ecart d exemptions est vu", any("exemptions" in l for l in trouves), True)

        # Le sens NEGATIF : deux gardes identiques ne rendent aucun ecart. Sans ce cas, un releve
        # qui crierait toujours passerait les trois premiers sans rien prouver.
        (r / "a/scripts/adr/verifie_apostrophe.py").write_text(
            'from _commun import RACINES, rapporte\nADR = "1"\nrapporte(ADR, "densite ici", [])\n',
            encoding="utf-8",
        )
        (r / "b/scripts/adr/cliquet-apostrophe.py").write_text(
            'from _commun import RACINES, rapporte\nADR = "1"\nrapporte(ADR, "densite ici", [])\n',
            encoding="utf-8",
        )
        verifie(
            "deux gardes identiques ne rendent aucun ecart",
            ecarts(contrats(r / "a"), contrats(r / "b")),
            [],
        )

    return echecs


if __name__ == "__main__":
    if "--auto-test" in sys.argv:
        raise SystemExit(_auto_test())
    ici = contrats(RACINE)
    if len(sys.argv) > 1:
        autre = pathlib.Path(sys.argv[1]).expanduser().resolve()
        if not (autre / "scripts" / "adr").is_dir():
            raise SystemExit(
                "%s ne porte pas de scripts/adr/ : ce n est pas un arbre comparable." % autre
            )
        lignes = ecarts(ici, contrats(autre))
        print("Ecarts de contrat entre les deux arbres, appariés par le geste :\n")
        print("\n".join("  " + l for l in lignes) if lignes else "  Aucun écart.")
        print("\n%d écart(s) sur %d geste(s) ici." % (len(lignes), len(ici)))
        print("Le PREDICAT n'est pas comparé : deux motifs ne se confrontent pas mécaniquement.")
        print(
            "L'appariement se fait par le geste : deux gardes du même sujet sous deux gestes"
            " différents sortent en deux absences, à rapprocher à la lecture."
        )
    else:
        print("Contrats des gardes de cet arbre :\n")
        for g, c in sorted(ici.items()):
            print(
                "  %-38s %-22s %-16s exempt=%-3s %s"
                % (g, c["population"], c["seuil"], c["exemptions"], c["verdict"][:34])
            )
        print("\n%d garde(s)." % len(ici))
