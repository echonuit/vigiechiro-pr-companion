#!/usr/bin/env python3
"""Garde du registre des blocs de javadoc RELUS et gardes volontairement (#4414).

## Ce qu il ferme

L ADR 4359 dit que son cliquet ne descendra pas a zero : certains blocs depassent le seuil et le
meritent. Rien ne distinguait pourtant un bloc qu on avait OUVERT et decide de garder d un bloc qu on
n avait JAMAIS LU. Chaque tranche de resorption rouvrait les deux et rejugeait, sur 680 blocs.

Le registre memorise la lecture. Ce garde le tient honnete.

## Ce qu il refuse

Une entree qui ne correspond a AUCUN bloc du corpus. Deux causes, et les deux demandent une action :

- **le bloc a change** - son empreinte porte sur son texte, donc toute edition l invalide. C est
  voulu : une prose reecrite n a pas ete relue sous sa forme actuelle, et la presumer valide serait
  exactement le faux vert que le registre existe pour eviter ;
- **le bloc a disparu** - renomme, deplace, ou passe sous le seuil. L entree ne garde plus rien et
  encombre la lecture du registre.

Sans ce refus, le registre accumulerait des lignes mortes et personne ne le saurait.

## Ce qu il NE FAIT PAS, et c est la decision

**Il ne fait pas baisser le cliquet.** La dette reste annoncee en entier, 3 248 lignes. Le registre
dit « ce bloc a ete lu », pas « cette dette n existe plus ».

Confondre les deux transformerait une **memoire de revue** en **desserrement**, et le depot separe
precisement ces deux gestes : un cliquet qui baisse est une bonne nouvelle mesuree, une exemption
est une decision. Les melanger ferait descendre le compteur sans qu une seule ligne ait ete corrigee.

## Pourquoi l empreinte, et pas la ligne ni le nom du membre

Trois formes ont ete confrontees avant celle-ci, et chacune produit un faux vert :

- `Fichier.java:120` : une insertion en amont decale, et un AUTRE bloc herite de l exemption ;
- `Fichier.java` + nom du membre : une surcharge `parse(String, Option)` herite de l exemption de
  `parse(String)`, sans que personne l ait decide ;
- une marque dans le bloc : le copier-coller la transporte vers un bloc jamais relu, et la PR ne
  touche pas le fichier du garde - donc aucun signal central, ce que l ADR 4339 exige.

L empreinte porte sur le texte : insensible aux decalages, insensible aux surcharges, et invalidee
par toute edition. Mesure avant de la retenir : 680 blocs sous cliquet, **680 empreintes distinctes,
aucune collision**.

## Sa cecite declaree

Elle ne voit pas un bloc devenu faux parce que le **CODE** a change sous lui : le texte du bloc n a
pas bouge, donc son empreinte non plus. C est la limite que `loupe-4359-javadoc-vieillie.py` couvre,
et les deux dispositifs se lisent ensemble.
"""

import hashlib
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import importlib.util  # noqa: E402

RACINE = pathlib.Path(__file__).resolve().parents[2]
REGISTRE = pathlib.Path("scripts/adr/4359-blocs-relus.tsv")

_spec = importlib.util.spec_from_file_location(
    "garde4359", pathlib.Path(__file__).parent / "4359-javadoc-narratif.py"
)
_garde = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_garde)


def empreinte(lignes: list[str]) -> str:
    """L empreinte d un bloc : son texte, marges retirees, sur seize caracteres.

    Les marges sont retirees pour qu une reindentation - un bloc qui descend d un niveau parce que sa
    classe gagne une englobante - n invalide pas une lecture qui reste valable. Tout le reste compte.
    """
    return hashlib.sha256("\n".join(l.strip() for l in lignes).encode()).hexdigest()[:16]


def blocs_du_corpus(racine: pathlib.Path = None) -> dict[str, str]:
    """Empreinte -> chemin, pour chaque bloc SOUS CLIQUET du code de production."""
    base = racine or RACINE
    trouves = {}
    # Les DEUX arbres, comme le cliquet qu il accompagne : un registre qui ne couvrirait que la
    # production laisserait un bloc de test compte dans la dette sans pouvoir jamais etre inscrit
    # comme relu. Le registre suit le corpus, sinon il en exclut une part en silence.
    arbres = [base / "src/main/java", base / "src/test/java"]
    for fichier in sorted(f for a in arbres if a.is_dir() for f in a.rglob("*.java")):
        lignes = fichier.read_text(encoding="utf-8").split("\n")
        for depart, prose in _garde.blocs(fichier):
            if prose <= _garde.SEUIL:
                continue
            j = depart - 1
            k = j
            while k < len(lignes) and lignes[k].strip().startswith("///"):
                k += 1
            trouves[empreinte(lignes[j:k])] = str(fichier.relative_to(base))
    return trouves


def entrees(racine: pathlib.Path = None) -> list[tuple[str, str, str]]:
    """Les lignes du registre, hors commentaires et lignes vides."""
    fichier = (racine or RACINE) / REGISTRE
    if not fichier.exists():
        return []
    lues = []
    for ligne in fichier.read_text(encoding="utf-8").split("\n"):
        if not ligne.strip() or ligne.startswith("#"):
            continue
        parts = ligne.split("\t")
        if len(parts) != 3:
            raise SystemExit(f"registre : ligne mal formee, trois colonnes attendues -> {ligne[:60]}")
        lues.append(tuple(p.strip() for p in parts))
    return lues


def perimees(inscrites: list[tuple[str, str, str]], corpus: dict[str, str]) -> list[str]:
    """Les entrees qui ne correspondent a aucun bloc. Fonction PURE, pour le cas temoin."""
    return [f"{h}  {chemin}  ({motif})" for h, chemin, motif in inscrites if h not in corpus]


if __name__ == "__main__":
    corpus = blocs_du_corpus()
    inscrites = entrees()
    mortes = perimees(inscrites, corpus)
    relus = len(inscrites) - len(mortes)

    print(f"Registre des blocs relus : {relus} sur {len(corpus)} blocs sous cliquet")
    print(f"  restant a lire : {len(corpus) - relus}")

    if mortes:
        print(
            f"\nECHEC : {len(mortes)} entree(s) ne correspondent a aucun bloc. Le bloc a change - donc\n"
            "il n a pas ete relu sous sa forme actuelle - ou il a disparu. Relisez-le et reinscrivez\n"
            "son empreinte, ou retirez la ligne.\n",
            file=sys.stderr,
        )
        for m in mortes:
            print(f"  {m}", file=sys.stderr)
        sys.exit(1)
    sys.exit(0)
