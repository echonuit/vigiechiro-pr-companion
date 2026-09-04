#!/usr/bin/env python3
"""Aucun workflow n appelle `apt-get` directement, et le cache APT est BRANCHE (porté du bash).

Trois etapes de trois workflows ont pendu le meme jour sur la meme ligne - `apt-get update` -
jusqu au butoir de leur job, y compris sur `main`. Le miroir du runner rendait « Ign: » sur toutes
ses sources et APT attendait l archive amont sans rien dire. Chacune portait un nom qui parlait
d autre chose.

Le remede a ete applique a UNE des trois, et les deux autres ont continue de pendre le lendemain.
C est le motif qu on connait : une lecon apprise a un seul endroit. D ou une porte unique,
`installer_paquets.py`, et ce garde pour qu on ne la contourne pas.

Ce que ce garde ne dit PAS : que la porte suffise. Elle borne et reprend ; un runner sans reseau
echouera quand meme - en une minute et en le disant, au lieu d immobiliser une PR trois quarts
d heure.

## Le second controle : un cache qui a l air d un cache

Verifie a la main une fois, le cablage s est revele faux a deux endroits sur six : un job portait
deux caches sur le meme chemin - ils se seraient ecrases - et une etape d installation n avait pas
la variable, donc retelechargeait tout en ayant l air cachee.

## Ce que l action de cache ne rejoue pas, et ce que ca coute

`awalsh128/cache-apt-pkgs-action` restaure des fichiers ; elle ne garantit pas l execution des
scripts `postinst`. Pour `fonts-*`, `fc-cache` n est pas rejoue et les apercus rendent dans une
police de REPLI, sans que rien ne rougisse. `ffmpeg` a ete AJOUTE APRES COUP, et c est une lecon
payee : range parmi les « paquets de fichiers » en regardant son nom, sa fermeture de dependances
tire dix paquets de polices. Le premier run qui a trouve le cache a fait tomber cinq cas du banc de
recette, tous ceux qui ecrivent du texte dans une video. Le run precedent, cache froid, passait : la
panne n apparait qu au SECOND passage.

Ce qui compte n est donc pas le paquet demande, c est ce qu il TRAINE.

Usage : python3 .github/scripts/verifie_apt.py [--auto-test]
        APT_RACINE=<dir> python3 .github/scripts/verifie_apt.py
"""

from __future__ import annotations

import os
import pathlib
import re
import sys

RACINE = pathlib.Path(__file__).resolve().parents[2]
RACINE_INJECTEE = "APT_RACINE"

# La prose se retire AVANT de chercher : commentaires et `name:` d etape. Un `apt-get` cite la
# explique le defaut ou nomme le garde ; l interdire pousserait a ne plus l expliquer. Ce cas n est
# pas theorique : la premiere version se refusait ELLE-MEME, sur le nom de sa propre etape.
COMMENTAIRE = re.compile(r"#.*$")
NOM_D_ETAPE = re.compile(r"^[ \t]*-?[ \t]*name:.*$")

POST_INSTALL_COMPTE = ("fonts-", "flatpak", "ffmpeg")


def racine_de(racine: pathlib.Path | None = None) -> pathlib.Path:
    return racine or pathlib.Path(os.environ.get(RACINE_INJECTEE, RACINE))


def appels_directs(fichiers: list[pathlib.Path]) -> list[str]:
    """Les lignes de PROSE retirees, puis ce qui reste et parle d `apt-get`."""
    fautes = []
    for f in fichiers:
        for numero, ligne in enumerate(f.read_text(encoding="utf-8").splitlines(), 1):
            nue = NOM_D_ETAPE.sub("", COMMENTAIRE.sub("", ligne))
            if "apt-get" in nue:
                fautes.append(f"{f.name}:{numero}:{nue}")
    return fautes


def cache_mal_branche(fichiers: list[pathlib.Path]) -> list[str] | None:
    """Les ecarts de cablage du cache, ou None si PyYAML manque."""
    try:
        import yaml
    except ImportError:
        return None

    ecarts = []
    for chemin in fichiers:
        contenu = yaml.safe_load(chemin.read_text(encoding="utf-8"))
        for nomjob, job in ((contenu or {}).get("jobs") or {}).items():
            if not isinstance(job, dict):
                continue
            etapes = job.get("steps") or []
            nom = f"{chemin.name} / {nomjob}"

            # AVANT le filtre des installations : un job peut n employer que l action de cache, sans
            # aucune installation par la porte - et c est justement la qu une police mal placee
            # passerait. La premiere version de cette regle vivait apres, donc ne s executait jamais
            # pour ces jobs-la ; son cas restait vert.
            for etape in etapes:
                if not isinstance(etape, dict) or "awalsh128/cache-apt-pkgs-action" not in str(
                    etape.get("uses", "")
                ):
                    continue
                demandes = str((etape.get("with") or {}).get("packages", "")).split()
                risques = [q for q in demandes if q.startswith(POST_INSTALL_COMPTE)]
                if risques:
                    ecarts.append(
                        f"{nom} : {', '.join(risques)} passe(nt) par l'action de cache, qui n'exécute pas "
                        f"les scripts post-installation - à installer par installer_paquets.py"
                    )

            # `--auto-test` n installe RIEN : il eprouve la porte. L exiger d un cache ferait rougir
            # la CI sur une etape qui ne telecharge pas un octet.
            installs = [
                e
                for e in etapes
                if isinstance(e, dict)
                and "installer_paquets.py" in str(e.get("run", ""))
                and "--auto-test" not in str(e.get("run", ""))
            ]
            if not installs:
                continue
            caches = [
                e
                for e in etapes
                if isinstance(e, dict) and "actions/cache@" in str(e.get("uses", ""))
            ]
            if len(caches) != 1:
                ecarts.append(
                    f"{nom} : {len(caches)} cache(s) pour {len(installs)} installation(s) - il en faut UN, partagé"
                )
            sans = [e for e in installs if not (e.get("env") or {}).get("APT_CACHE")]
            if sans:
                ecarts.append(
                    f"{nom} : {len(sans)} installation(s) sans APT_CACHE - elles retéléchargent tout"
                )
    return ecarts


def juger(racine: pathlib.Path | None = None) -> int:
    """Les deux controles, dans l ordre, et le code de sortie qui va avec."""
    base = racine_de(racine)
    workflows = base / ".github" / "workflows"

    if not workflows.is_dir():
        print(f"✗ {workflows} introuvable : rien ne peut être vérifié.")
        return 1

    fichiers = sorted(workflows.glob("*.yml"))
    if not fichiers:
        print("✗ aucun workflow trouvé : la garde ne peut rien affirmer.")
        return 1

    fautes = appels_directs(fichiers)
    if fautes:
        print("✗ appel(s) direct(s) à apt-get dans un workflow :")
        for f in fautes:
            print(f"   · {f}")
        print()
        print(
            "  Passer par .github/scripts/installer_paquets.py : il borne les délais et reprend les"
        )
        print(
            "  téléchargements coupés. Trois étapes ont pendu jusqu'au butoir de leur job, sur main"
        )
        print("  comme sur les PR, faute de cette porte.")
        return 1

    ecarts = cache_mal_branche(fichiers)
    if ecarts is None:
        print("✗ PyYAML absent : la garde ne peut pas lire les workflows.")
        return 1
    if ecarts:
        print("✗ le cache APT est mal branché :")
        for e in ecarts:
            print(f"   · {e}")
        print()
        print(
            "  Un cache qui a l'air d'un cache et n'en est pas coûte le temps qu'il prétend gagner."
        )
        return 1

    print(
        f"✓ Aucun appel direct à apt-get, et le cache est branché : les {len(fichiers)} workflows "
        "passent par la porte."
    )
    return 0


PORTE_AVEC_CACHE = """jobs:
  a:
    steps:
      - uses: actions/cache@abc
      - env:
          APT_CACHE: /tmp/c
        run: python3 .github/scripts/installer_paquets.py bats
"""
APT_NU = "jobs:\n  a:\n    steps:\n      - run: sudo apt-get install -y bats\n"
APT_COMMENTE = (
    "jobs:\n  a:\n    # un apt-get nu pendait ici avant #4031\n    steps:\n      - run: echo ok\n"
)
SANS_APT_CACHE = """jobs:
  a:
    steps:
      - uses: actions/cache@abc
      - run: python3 .github/scripts/installer_paquets.py bats
"""
AUTO_TEST_DE_LA_PORTE = """jobs:
  a:
    steps:
      - run: python3 .github/scripts/installer_paquets.py --auto-test
"""


def _action_de_cache(paquets: str) -> str:
    return (
        "jobs:\n  a:\n    steps:\n      - uses: awalsh128/cache-apt-pkgs-action@abc\n"
        f"        with:\n          packages: {paquets}\n"
    )


DEUX_CACHES = """jobs:
  a:
    steps:
      - uses: actions/cache@abc
      - uses: actions/cache@abc
      - env:
          APT_CACHE: /tmp/c
        run: python3 .github/scripts/installer_paquets.py bats
"""


def _auto_test() -> int:
    """Les douze cas de la version bash, dont six qui DOIVENT rougir."""
    import contextlib
    import io
    import tempfile

    echecs = cas = rouges = 0
    print("AUTO-TEST")

    with tempfile.TemporaryDirectory(prefix="vc-apt-") as tmp:
        bac = pathlib.Path(tmp)
        flux = bac / ".github" / "workflows"
        flux.mkdir(parents=True)

        def verifie(attendu: int, libelle: str) -> None:
            nonlocal echecs, cas, rouges
            cas += 1
            if attendu != 0:
                rouges += 1
            with (
                contextlib.redirect_stdout(io.StringIO()),
                contextlib.redirect_stderr(io.StringIO()),
            ):
                code = juger(bac)
            if code == attendu:
                print(f"  [OK   ] {libelle:<52} -> {code}")
            else:
                print(f"  [ÉCHEC] {libelle:<52} -> {code} (attendu {attendu})")
                echecs += 1

        # La fixture de reference porte AUSSI son cache : depuis que le garde verifie le cablage,
        # une installation sans cache est une faute, et un exemple « bon » qui n en aurait pas
        # serait faux.
        (flux / "bon.yml").write_text(PORTE_AVEC_CACHE, encoding="utf-8")
        verifie(0, "passer par la porte est accepté")

        (flux / "nu.yml").write_text(APT_NU, encoding="utf-8")
        verifie(1, "un apt-get direct est refusé")
        (flux / "nu.yml").unlink()
        verifie(0, "le dépôt redevient conforme quand on le retire")

        # Un `apt-get` cite dans un COMMENTAIRE explique le defaut : l interdire pousserait a ne
        # plus l expliquer, ce qui est le contraire du but.
        (flux / "commente.yml").write_text(APT_COMMENTE, encoding="utf-8")
        verifie(0, "un apt-get en COMMENTAIRE reste permis")

        # --- le cache, et ce qui le rend inutile sans le dire ---
        (flux / "cache.yml").write_text(PORTE_AVEC_CACHE, encoding="utf-8")
        verifie(0, "un cache branché est accepté")

        (flux / "cache.yml").write_text(SANS_APT_CACHE, encoding="utf-8")
        verifie(1, "une installation SANS APT_CACHE est refusée")

        # La decision elle-meme se garde. Sans ce cas, quelqu un basculerait les polices sur
        # l action rapide pour gagner vingt secondes, et les apercus rendraient dans un repli.
        (flux / "cache.yml").write_text(AUTO_TEST_DE_LA_PORTE, encoding="utf-8")
        verifie(0, "l auto-test de la porte n exige aucun cache")

        (flux / "cache.yml").write_text(_action_de_cache("fonts-noto-core"), encoding="utf-8")
        verifie(1, "une police par l action de cache est refusée")

        # ffmpeg est justement l exemple qui a coute cinq cas rouges : il TRAINE dix paquets de
        # polices. Ce cas garde la lecon.
        (flux / "cache.yml").write_text(_action_de_cache("ffmpeg xdotool"), encoding="utf-8")
        verifie(1, "ffmpeg par l action de cache est refusé")

        (flux / "cache.yml").write_text(_action_de_cache("bats xdotool"), encoding="utf-8")
        verifie(0, "des paquets sans post-install : accepté")

        (flux / "cache.yml").write_text(DEUX_CACHES, encoding="utf-8")
        verifie(1, "DEUX caches dans un job sont refusés")
        (flux / "cache.yml").unlink()

        for f in flux.glob("*.yml"):
            f.unlink()
        verifie(1, "sans aucun workflow, la garde REFUSE au lieu de passer")

    print()
    print(f"{cas} cas, dont {rouges} qui DOIVENT rougir.")
    if echecs == 0:
        print("Auto-test concluant.")
        return 0
    print(f"AUTO-TEST EN ÉCHEC ({echecs}) : ne pas se fier au verdict de cette garde.")
    return 1


if __name__ == "__main__":
    if "--auto-test" in sys.argv[1:2]:
        sys.exit(_auto_test())
    sys.exit(juger())
