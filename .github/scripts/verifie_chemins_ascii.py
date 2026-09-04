#!/usr/bin/env python3
"""Un chemin suivi ne porte que de l ASCII (#5089, porte du bash en #5231).

## Pourquoi ce garde existe

`git ls-files` et `git diff --name-only` ECHAPPENT les chemins non-ASCII par defaut :
`brief/docs/Objectifs qualit\\303\\251s/...` la ou le disque porte `Objectifs qualites`. Tout
outillage qui teste ensuite l existence du fichier rejette la ligne, EN SILENCE. Le 2026-09-01, une
passe de mise a jour du graphe a ainsi ecarte 50 fichiers et pris 6 renommages pour 56 suppressions.
Rien n a rougi : le corpus etait seulement incomplet.

## Ce qu il refuse, et ce qu il laisse passer

Il refuse le seul octet qui cause le defaut : tout ce qui sort de l ASCII dans un chemin SUIVI.

Il ne dit RIEN de la casse, des espaces, des apostrophes ni des tirets. `core.quotePath` ne les
echappe pas, ils ne sont pas la cause, et `C12`, `P17`, `M-CompteRendu` sont des identifiants, pas
des phrases a normaliser. Un garde qui refuserait plus large refuserait sans mesure.

## Tolerance zero, et pourquoi c est tenable

Les 101 chemins fautifs ont ete renommes par #5089. La zone est a zero le jour de la decision, donc
le seuil est zero et non un cliquet : c est la regle de l ADR sur les cliquets, qui reserve le
cliquet aux zones qu on ne peut pas vider d un coup.

Usage : verifie_chemins_ascii.py              (juge le depot)
        verifie_chemins_ascii.py --auto-test  (s eprouve lui-meme)
"""

from __future__ import annotations

import os
import pathlib
import subprocess
import sys

LISTE_INJECTEE = "CHEMINS_FICHIER"


def chemins_suivis(liste: pathlib.Path | None = None) -> list[str]:
    """La liste des chemins suivis, injectable pour l auto-test.

    `-c core.quotePath=false` n est pas un detail : sans lui git rend les chemins fautifs DEJA
    echappes en ASCII pur, et ce garde les declarerait conformes. Il serait vert precisement sur ce
    qu il doit refuser.
    """
    depuis = liste or (
        pathlib.Path(os.environ[LISTE_INJECTEE]) if os.environ.get(LISTE_INJECTEE) else None
    )
    if depuis is not None:
        return depuis.read_text(encoding="utf-8").splitlines()
    rendu = subprocess.run(
        ["git", "-c", "core.quotePath=false", "ls-files"],
        capture_output=True,
        text=True,
        check=True,
    )
    return rendu.stdout.splitlines()


def juger(liste: pathlib.Path | None = None) -> int:
    """Le verdict, et le code de sortie qui va avec."""
    fautifs = [c for c in chemins_suivis(liste) if any(ord(x) > 0x7F for x in c)]

    if not fautifs:
        print("Tous les chemins suivis sont en ASCII.")
        return 0

    print("REFUS : des chemins suivis portent des caracteres non-ASCII.")
    print()
    for c in fautifs:
        print(f"  {c}")
    print()
    print("Ces chemins sont invisibles a toute commande git qui les liste sans")
    print("« -c core.quotePath=false », et le rejet est SILENCIEUX.")
    print("Renommez-les en translitterant les lettres accentuees (e pour e accent aigu, etc.).")
    return 1


# (attendu, libelle, contenu de la liste). Les cas qui comptent sont les ROUGES : sans eux, tous
# les verts ne valent rien.
CAS = (
    (
        "rouge",
        "un nom de fichier accentue est refuse",
        "src/Ok.java\nbrief/docs/Modele/C10 - Releve climatique.md\nbrief/docs/Modèle/x.md\n",
    ),
    (
        "rouge",
        "un REPERTOIRE accentue est refuse, meme si le fichier est propre",
        "brief/docs/Objectifs qualités/index.md\n",
    ),
    ("rouge", "un caractere non-latin est refuse aussi", "docs/日本.md\n"),
    (
        "ok",
        "des espaces et des majuscules passent : ils ne sont pas la cause",
        "brief/docs/Analyse et conception/M-CompteRendu.md\nbrief/README.md\n",
    ),
    ("ok", "une apostrophe droite passe", "brief/docs/C3 - Point d'ecoute.md\n"),
    ("ok", "une liste vide passe", ""),
)


def _auto_test() -> int:
    """Les six cas injectes, puis LE CHEMIN REEL, celui qu aucun d eux n exerce."""
    import contextlib
    import io
    import tempfile

    echecs = 0
    cas = rouges = 0
    with tempfile.TemporaryDirectory(prefix="vc-ascii-") as tmp:
        fichier = pathlib.Path(tmp) / "liste"
        for attendu, libelle, contenu in CAS:
            cas += 1
            if attendu != "ok":
                rouges += 1
            fichier.write_text(contenu, encoding="utf-8")
            with (
                contextlib.redirect_stdout(io.StringIO()),
                contextlib.redirect_stderr(io.StringIO()),
            ):
                code = juger(fichier)
            obtenu = "ok" if code == 0 else "rouge"
            if obtenu == attendu:
                print(f"  OK  {libelle}")
            else:
                print(f"  KO  {libelle} : attendu {attendu}, obtenu {obtenu}")
                echecs = 1

    # LE CHEMIN REEL, et non le leurre (ADR 4331). Les cas ci-dessus injectent tous la liste et
    # n exercent JAMAIS `git ls-files`. Sans ce cas, la fonction qui interroge git n est eprouvee
    # par rien - et c est elle qui porte le `-c core.quotePath=false` dont tout depend.
    cas += 1
    with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
        reel = juger()
    if reel == 0:
        print("  OK  le chemin reel (git ls-files) conclut sur le depot")
    else:
        print("  KO  le chemin reel refuse le depot : des chemins non-ASCII y subsistent")
        echecs = 1

    print()
    print(f"{cas} cas, dont {rouges} qui doivent rougir.")
    return echecs


if __name__ == "__main__":
    if "--auto-test" in sys.argv[1:2]:
        sys.exit(_auto_test())
    sys.exit(juger())
