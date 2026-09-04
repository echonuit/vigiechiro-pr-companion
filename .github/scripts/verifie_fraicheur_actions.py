#!/usr/bin/env python3
"""La fraicheur des actions epinglees, par leur version ET par l age du commit (#5221, porte du bash).

Une action epinglee par SHA ne dit pas son age. Deux mesures le disent, et il faut les deux :

- **la version**, quand le tag veut dire quelque chose : une majeure de retard bloque, une mineure
  avertit sans bloquer - sinon le garde rougirait a chaque release amont, et on apprendrait a ne
  plus le lire ;
- **l age du commit epingle** face au HEAD amont, qui voit ce que les tags cachent.

## Le cas qui a impose la seconde mesure (#2213)

`winget-releaser` n a qu un tag `v2`, immobile depuis novembre 2024, pendant que l action installait
un `komac` de mars 2026. Tag epingle = tag amont = `v2` : **aucun ecart de version a signaler**, et
vingt et un mois de retard reel. Un tag qui ne bouge jamais rend la premiere mesure aveugle.

## Ses trois interfaces, et pourquoi elles restent

`--inventaire` rend l inventaire brut, `--racine` change l arbre lu, et le mode par defaut **lit son
entree sur stdin** : la mesure amont demande le reseau, elle se fait ailleurs, et ce garde ne fait
que juger ce qu on lui donne. Separer les deux est ce qui rend ses treize cas jouables hors ligne.

## Ce qu il ne juge pas

Les references NON epinglees. Les refuser est le travail de `verifie-epinglage.sh`, et deux gardes
qui se disputent le meme constat finissent par diverger.
"""

from __future__ import annotations

import pathlib
import re
import sys

RACINE = pathlib.Path(__file__).resolve().parents[2]

# Seuils sur l AGE du commit epingle face au HEAD amont. Calibres sur une mesure du 2026-08-11, ou
# le pire ecart du depot etait de 143 jours : le garde est donc muet sur un depot sain, et le cas
# qui lui avait echappe (608 jours) est rouge.
AGE_AVERTISSEMENT = 180
AGE_ROUGE = 365

EPINGLAGE = re.compile(r"uses:\s*([a-zA-Z0-9_.-]+/[a-zA-Z0-9_.-]+)@([a-f0-9]{40})(?:\s+#\s*(\S+))?")
VERSION = re.compile(r"^v?[0-9]")


def inventorier(racine: pathlib.Path | None = None) -> list[str]:
    """Une ligne `depot<TAB>sha<TAB>commentaire` par action epinglee par SHA, dedoublonnee."""
    base = racine or RACINE
    dossier = base / ".github" / "workflows"
    lignes = set()
    if dossier.is_dir():
        for chemin in sorted(dossier.rglob("*")):
            if not chemin.is_file():
                continue
            for depot, sha, commentaire in EPINGLAGE.findall(
                chemin.read_text(encoding="utf-8", errors="ignore")
            ):
                lignes.add(f"{depot}\t{sha}\t{commentaire or ''}")
    return sorted(lignes)


def _majeure(version: str) -> str:
    return re.sub(r"^v?([0-9]+).*", r"\1", version)


def juger(inventaire: str) -> int:
    """Le verdict sur un inventaire `depot<TAB>epingle<TAB>amont<TAB>retard<TAB>commentaire`."""
    if not inventaire.strip():
        print("❌ Inventaire vide : aucune action examinée.")
        print("   Ce n'est pas « tout est à jour », c'est « la question n'a pas été posée ».")
        print("   Regarder l'extraction (--inventaire) et le droit de lecture sur l'API GitHub.")
        return 1

    rouges = avertis = ajour = lignes = 0
    sortie_rouge: list[str] = []
    sortie_avertie: list[str] = []

    for ligne in inventaire.split("\n"):
        champs = ligne.split("\t")
        depot = champs[0] if champs else ""
        if not depot:
            continue
        epingle, amont, retard, commentaire = (list(champs[1:]) + ["", "", "", ""])[:4]
        lignes += 1
        motif_rouge = ""
        motif_avert = ""

        # ---- 1. Les tags, quand ils veulent dire quelque chose ------------------------------
        if not epingle or epingle == "?":
            # Notre SHA ne porte aucun tag. Deux cas tres differents, que le COMMENTAIRE separe.
            if not commentaire:
                # Sans commentaire, on ne peut pas distinguer « tag disparu » de « epinglage hors
                # tag assume ». On ne tranche donc pas en faveur du rassurant : c est le silence
                # qu on combat.
                motif_rouge = (
                    "version indéterminée (aucun tag sur le SHA, aucun commentaire pour dire "
                    "l'intention)"
                )
            elif VERSION.match(commentaire):
                # Le commentaire annonce une version : le tag a donc ete deplace ou supprime.
                motif_rouge = (
                    f"le commentaire annonce « {commentaire} » mais le SHA ne porte plus aucun tag"
                )
            # Sinon (`# main @ …`) : epinglage hors tag ASSUME. L age tranchera.
        elif not amont or amont == "?":
            motif_rouge = f"version indéterminée en amont (épinglé « {epingle} »)"
        elif epingle != amont:
            if _majeure(epingle) != _majeure(amont):
                motif_rouge = f"{epingle} -> {amont} (une MAJEURE de retard)"
            else:
                motif_avert = f"{epingle} -> {amont}"

        # ---- 2. L AGE, qui voit ce que les tags cachent -------------------------------------
        if retard:
            if retard.isdigit():
                age = int(retard)
                vieux = f"commit épinglé vieux de {age} jours face au HEAD amont"
                if age >= AGE_ROUGE:
                    motif_rouge = f"{motif_rouge} ; {vieux}" if motif_rouge else vieux
                elif age >= AGE_AVERTISSEMENT:
                    motif_avert = f"{motif_avert} ; {vieux}" if motif_avert else vieux
            else:
                motif_rouge = (
                    f"{motif_rouge} ; âge indéterminé" if motif_rouge else "âge indéterminé"
                )

        if motif_rouge:
            sortie_rouge.append(f"   {depot} : {motif_rouge}")
            rouges += 1
        elif motif_avert:
            sortie_avertie.append(f"   {depot} : {motif_avert}")
            avertis += 1
        else:
            ajour += 1

    if rouges:
        print(f"❌ {rouges} action(s) à regarder :")
        print("\n".join(sortie_rouge))
    if avertis:
        print(f"⚠️  {avertis} action(s) en retard dans la même majeure (signalé, non bloquant) :")
        print("\n".join(sortie_avertie))
    print(
        f"Fraîcheur des épinglages : {lignes} action(s) examinée(s), {ajour} à jour, "
        f"{avertis} en retard mineur, {rouges} bloquante(s)."
    )
    return 1 if rouges else 0


ESSAI = """jobs:
  a:
    steps:
      - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7
      - uses: actions/setup-node@820762786026740c76f36085b0efc47a31fe5020 # v7.0.0
      - uses: quelquun/action-flottante@v3
"""

# (nom, inventaire, attendu, fragment EXIGE dans la sortie). Le fragment compte autant que le code :
# un `1` peut venir du script lui-meme, et ce depot s est deja fait prendre a lire un plantage comme
# une detection.
CAS = (
    (
        "tout à jour",
        "actions/checkout\tv7.0.1\tv7.0.1\nactions/setup-java\tv5.7.0\tv5.7.0",
        "vert",
        "2 action(s) examinée(s), 2 à jour",
    ),
    # Le cas reel du 2026-08-06, rejoue.
    (
        "une majeure de retard",
        "actions/attest-build-provenance\tv3.0.0\tv4.1.1\nactions/checkout\tv7.0.1\tv7.0.1",
        "rouge",
        "une MAJEURE de retard",
    ),
    # Un retard mineur ne bloque pas : sinon la garde rougirait a chaque release amont.
    ("retard mineur, non bloquant", "actions/checkout\tv7.0.1\tv7.2.0", "vert", "non bloquant"),
    # Une mesure ratee n est pas un « a jour ».
    ("amont introuvable", "actions/checkout\tv7.0.1\t?", "rouge", "version indéterminée"),
    ("épinglé introuvable", "actions/checkout\t\tv7.0.1", "rouge", "version indéterminée"),
    ("inventaire vide", "", "rouge", "Inventaire vide"),
    # L AGE : ce que les tags cachent (#2213), le cas vecu rejoue.
    (
        "un tag immobile masque un commit de 608 jours",
        "vedantmgoyal9/winget-releaser\tv2\tv2\t608\tv2",
        "rouge",
        "vieux de 608 jours",
    ),
    (
        "âge au-dessus du seuil d avertissement, non bloquant",
        "anchore/scan-action\tv7\tv7\t200\tv7",
        "vert",
        "non bloquant",
    ),
    # Controles NEGATIFS : la regle doit rester etroite.
    ("un âge sous les seuils ne dit rien", "actions/checkout\tv7\tv7\t143\tv7", "vert", "1 à jour"),
    # Epinglage hors tag ASSUME : le commentaire ne pretend pas etre une version, l age est frais.
    (
        "épinglage sur main, récent, accepté",
        "vedantmgoyal9/winget-releaser\t?\tv2\t0\tmain @ 2026-07-28",
        "vert",
        "1 à jour",
    ),
    # Mais un commentaire qui ANNONCE une version que le SHA ne porte plus reste une nouvelle.
    (
        "le tag annoncé a disparu en amont",
        "actions/checkout\t?\tv7\t3\tv7",
        "rouge",
        "ne porte plus aucun tag",
    ),
    # Et sans commentaire du tout, on ne conclut pas au rassurant.
    (
        "ni tag ni commentaire",
        "actions/checkout\t?\tv7\t3\t",
        "rouge",
        "aucun commentaire pour dire",
    ),
)


def _auto_test() -> int:
    """Les treize cas de la version bash, dont sept rouges verifies sur leur MESSAGE."""
    import contextlib
    import io
    import tempfile

    echecs = 0
    for nom, inventaire, attendu, fragment in CAS:
        tampon = io.StringIO()
        with contextlib.redirect_stdout(tampon):
            code = juger(inventaire)
        sortie = tampon.getvalue()
        obtenu = "rouge" if code else "vert"
        if obtenu != attendu:
            print(f"❌ autotest « {nom} » : attendu {attendu}, obtenu {obtenu}")
            print("\n".join("      " + l for l in sortie.split("\n")))
            echecs += 1
            continue
        if fragment not in sortie:
            print(
                f"❌ autotest « {nom} » : {obtenu} attendu et obtenu, mais le message ne dit pas "
                f"« {fragment} »"
            )
            print("\n".join("      " + l for l in sortie.split("\n")))
            echecs += 1

    # Le MODE INVENTAIRE, sur des workflows fabriques : il voit les epinglages et ignore le reste.
    with tempfile.TemporaryDirectory(prefix="vc-frai-") as tmp:
        bac = pathlib.Path(tmp)
        (bac / ".github" / "workflows").mkdir(parents=True)
        (bac / ".github" / "workflows" / "essai.yml").write_text(ESSAI, encoding="utf-8")
        inv = inventorier(bac)
    if len(inv) != 2:
        print(f"❌ autotest « inventaire » : 2 épinglages attendus, {len(inv)} vu(s)")
        print("\n".join("      " + l for l in inv))
        echecs += 1
    elif not any("actions/setup-node" in l and "v7.0.0" in l for l in inv):
        print("❌ autotest « inventaire » : le commentaire de version n'est pas remonté")
        echecs += 1
    if any("action-flottante" in l for l in inv):
        print("❌ autotest « inventaire » : une référence NON épinglée a été inventoriée")
        echecs += 1

    if echecs:
        print(f"Autotest de la fraîcheur : {echecs} échec(s).")
        return 1
    print("Autotest de la fraîcheur : OK (13 cas, dont 7 rouges vérifiés sur leur message).")
    return 0


if __name__ == "__main__":
    mode = "juger"
    racine = RACINE
    args = sys.argv[1:]
    while args:
        drapeau = args.pop(0)
        if drapeau == "--auto-test":
            sys.exit(_auto_test())
        elif drapeau == "--inventaire":
            mode = "inventaire"
        elif drapeau == "--racine":
            racine = pathlib.Path(args.pop(0))
        else:
            print(f"option inconnue : {drapeau}", file=sys.stderr)
            sys.exit(2)
    if mode == "inventaire":
        for ligne in inventorier(racine):
            print(ligne)
        sys.exit(0)
    sys.exit(juger(sys.stdin.read()))
