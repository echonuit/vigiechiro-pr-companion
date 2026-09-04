#!/usr/bin/env python3
"""Installe des paquets APT sur un runner, sans pendre quand un miroir tombe (porte du bash en #5240).

## Pourquoi cette porte unique existe

Trois etapes de trois workflows differents ont pendu le meme jour, sur la meme ligne :
`apt-get update`. Le miroir Azure du runner rendait `Ign:` sur toutes ses sources, APT basculait sur
l archive amont, et l attente durait jusqu au butoir du job - 12 minutes pour `banc-filme`, 30 pour
`capturer`, 40 pour `paquet`, y compris SUR `main`.

Chacune portait un nom qui parlait d autre chose. Lu de loin, le rouge accusait le code. Le log disait
« Get:5 … InRelease » puis trente-huit minutes de silence.

## Ce que cette porte fait, et ce qu elle ne fait pas

Elle **borne** : des delais courts font echouer vite plutot que trainer, et des reprises rattrapent un
telechargement coupe. Elle ne rend pas un miroir mort vivant - un runner sans reseau echouera, mais en
une minute et en le DISANT, au lieu d immobiliser une PR trois quarts d heure.

Jamais de `-qq` : c est lui qui a rendu la premiere panne indechiffrable. Une etape muette qui pend
n apprend rien a personne.

## Le CACHE, et c est lui qui regle le fond

Borner et reprendre evite de pendre ; cela ne fait pas descendre 91 Mo plus vite. Les runners
ralentissent avant de bloquer - la panne est en amont, chez l hebergeur - et le seul levier qui reste
est de NE PAS RETELECHARGER. `APT_CACHE` designe un dossier que le workflow fait survivre d un run a
l autre. Absent, tout fonctionne comme avant.

## Le volume ne se recalcule PAS ici

Cette porte l a annonce un temps - « 87 Mo a telecharger » - en sommant la taille des paquets. Le
chiffre etait juste comme POIDS et faux comme ANNONCE : le cache servant, APT n en telechargeait
aucun, et la ligne d a cote disait « Need to get 0 B/91.2 MB ». Deux mesures voisines qui se
contredisent, dont une seule fait foi.

## Le nom de ce fichier est LU par un garde

`verifie_apt.py` reconnait une installation par la porte a la chaine `installer_paquets.py` dans un
`run:`, et exempte `--auto-test` de l exigence de cache. Changer ce nom sans reprendre ces deux
chaines rendrait ce garde muet sans qu il rougisse.

Usage : python3 .github/scripts/installer_paquets.py [--avec-recommandations] <paquet>...
        python3 .github/scripts/installer_paquets.py --auto-test
"""

from __future__ import annotations

import pathlib
import subprocess
import sys

# Bornes : au-dela, on prefere un echec net a une attente muette.
BORNES = [
    "-o",
    "Acquire::Retries=3",
    "-o",
    "Acquire::http::Timeout=20",
    "-o",
    "Acquire::https::Timeout=20",
]


def installer(paquets: list[str], recommandations: bool = False, cache: str = "") -> int:
    """Les deux essais, et le code de sortie qui va avec."""
    if not paquets:
        print("installer_paquets.py : aucun paquet demandé.", file=sys.stderr)
        return 1

    bornes = list(BORNES)
    if cache:
        dossier = pathlib.Path(cache)
        (dossier / "partial").mkdir(parents=True, exist_ok=True)
        # APT tourne sous sudo : sans ces droits, il ne sait pas ecrire dans un dossier du runner, et
        # il retelechargerait en silence - un cache qui a l air d un cache et n en est pas. Le
        # dossier nous appartient, donc un chmod nu suffit : reclamer sudo ici ferait echouer le
        # script sur un poste de developpement sans terminal.
        subprocess.run(["chmod", "-R", "777", str(dossier)], capture_output=True, check=False)
        bornes += ["-o", f"Dir::Cache::archives={dossier}"]
        deja = len(list(dossier.glob("*.deb")))
        print(f"→ cache APT : {dossier} ({deja} paquet(s) déjà là)", flush=True)

    # On n installe pas ce qui est deja la. Le runner GitHub porte deja `xvfb` : le demander le
    # faisait resoudre, comparer, et parfois retelecharger des dependances pour rien.
    manquants = []
    for paquet in paquets:
        if subprocess.run(["dpkg", "-s", paquet], capture_output=True, check=False).returncode == 0:
            print(f"→ {paquet} : déjà installé sur ce runner", flush=True)
        else:
            manquants.append(paquet)
    if not manquants:
        print("→ rien à installer : tout est déjà là.", flush=True)
        return 0

    # `flush` sur les lignes ci-dessus et ci-dessous : sans lui, elles sortent APRES celles de
    # `apt-get` et apres les messages d echec, parce que la sortie standard de Python est mise en
    # tampon par blocs des qu elle n est pas un terminal - et un journal de CI n en est jamais un.
    # Mesure faite avec un `apt-get` de comptoir : « essai 1/2 » s affichait sous « echec de
    # l essai 1 », soit l inverse de l ordre reel. Le bash n avait pas ce probleme.
    sans_recos = [] if recommandations else ["--no-install-recommends"]
    for essai in (1, 2):
        print(f"→ apt-get update (essai {essai}/2)", flush=True)
        if subprocess.run(["sudo", "apt-get", "update", *bornes], check=False).returncode == 0:
            print(f"→ apt-get install : {' '.join(manquants)}", flush=True)
            if (
                subprocess.run(
                    ["sudo", "apt-get", "install", "-y", *sans_recos, *bornes, *manquants],
                    check=False,
                ).returncode
                == 0
            ):
                # Les droits se remettent a plat APRES l installation : `actions/cache` archive le
                # dossier en tant qu utilisateur ordinaire, et des `.deb` deposes par root sous des
                # droits stricts ne seraient pas lisibles - le cache se remplirait sans jamais servir.
                if cache:
                    subprocess.run(["sudo", "chmod", "-R", "a+rX", cache], check=False)
                return 0
        print(f"   … échec de l'essai {essai}", file=sys.stderr)

    print(f"✗ Installation impossible après deux essais : {' '.join(manquants)}", file=sys.stderr)
    print(
        "  Signature habituelle : le miroir du runner ne répond plus (« Ign: » sur toutes les sources).",
        file=sys.stderr,
    )
    print("  Ce n'est pas le code de la PR qui est en cause.", file=sys.stderr)
    return 1


def _auto_test() -> int:
    """Neuf cas, et ce qu ils n eprouvent PAS est dit ici.

    Cette porte est un UTILITAIRE, pas un garde : elle ne rend aucun verdict, et l ADR 3661 ne s y
    applique pas litteralement. Ce qui la rend quand meme digne de cas est son RAYON : elle est en
    travers du chemin de tous les workflows, y compris ceux qui publient, et un defaut y serait
    indiscernable d une panne de miroir.

    Ce que ces cas n eprouvent PAS : la reprise apres un echec reseau, ni l installation elle-meme.
    Les deux demandent un vrai apt et un vrai miroir ; les feindre ferait un vert qui ne prouve rien.
    C est la CI qui les exerce, a chaque run.
    """
    import contextlib
    import io
    import tempfile

    echecs = cas = rouges = 0
    print("AUTO-TEST")

    def joue(*arguments, **nommes) -> tuple[str, int]:
        tampon = io.StringIO()
        with contextlib.redirect_stdout(tampon), contextlib.redirect_stderr(tampon):
            code = installer(*arguments, **nommes)
        return tampon.getvalue(), code

    def essai(libelle: str, attendu: str, obtenu_vert: bool) -> None:
        nonlocal echecs, cas, rouges
        cas += 1
        if attendu == "rouge":
            rouges += 1
        obtenu = "vert" if obtenu_vert else "rouge"
        if obtenu == attendu:
            print(f"  [OK   ] {libelle:<52} -> {obtenu}")
        else:
            print(f"  [ÉCHEC] {libelle:<52} -> {obtenu} (attendu {attendu})")
            echecs += 1

    with tempfile.TemporaryDirectory(prefix="vc-apt-") as tmp:
        bac = pathlib.Path(tmp)

        # Le cas qui empeche un appel vide de passer pour une installation reussie.
        _, code = joue([])
        essai("aucun paquet demandé est refusé", "rouge", code == 0)

        # Des paquets que TOUT poste porte : on eprouve le saut, pas l installation.
        sortie, code = joue(["bash", "coreutils"])
        essai("des paquets déjà présents ne s installent pas", "vert", code == 0)
        essai("le saut se DIT, il ne se devine pas", "vert", "déjà installé" in sortie)
        essai("et il annonce qu il ne reste rien à faire", "vert", "rien à installer" in sortie)

        # Le cache : le dossier se cree, ses droits permettent a apt (sous sudo) d y ecrire.
        joue(["bash"], cache=str(bac / "cache"))
        essai("APT_CACHE crée son dossier et son partial", "vert", (bac / "cache/partial").is_dir())
        sortie, _ = joue(["bash"], cache=str(bac / "cache2"))
        essai("le cache annonce ce qu il porte déjà", "vert", "paquet(s) déjà là" in sortie)
        # Sans APT_CACHE, la porte ne doit RIEN dire du cache : un message de cache sur un poste qui
        # n en a pas ferait croire a un gain qui n existe pas.
        sortie, _ = joue(["bash"])
        essai("sans APT_CACHE, aucun message de cache", "rouge", "cache APT" in sortie)

        # Les recommandations : le drapeau change la commande, et son absence aussi.
        _, code = joue(["bash"], recommandations=True)
        essai("--avec-recommandations est accepté", "vert", code == 0)
        sortie, _ = joue([], recommandations=True)
        essai("et il ne compte pas comme un paquet", "rouge", "aucun paquet" not in sortie)

    print()
    print(f"{cas} cas, dont {rouges} qui DOIVENT rougir.")
    if echecs == 0:
        print("Auto-test concluant.")
        return 0
    print(f"AUTO-TEST EN ÉCHEC ({echecs}) : ne pas se fier au verdict de cette porte.")
    return 1


if __name__ == "__main__":
    import os

    if "--auto-test" in sys.argv[1:2]:
        sys.exit(_auto_test())
    arguments = sys.argv[1:]
    recommandations = False
    if arguments[:1] == ["--avec-recommandations"]:
        recommandations = True
        arguments = arguments[1:]
    sys.exit(installer(arguments, recommandations, os.environ.get("APT_CACHE", "")))
