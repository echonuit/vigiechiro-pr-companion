#!/usr/bin/env python3
"""Garde « portee d un secret de plateforme » (#4303, porte du bash en #5231).

## Ce qu elle empeche

Un secret Vigie-Chiro pose dans l `env:` d un **job** n est pas offert au pas qui en a besoin : il
est offert a **tout** ce que le job execute. Sur un job qui lance la suite de tests, cela veut dire
la suite entiere, pointee sur la production.

Le chemin est court et aucun de ses trois pas ne se voit en lisant le YAML :

1. `ConnexionModule.jetonPonctuel()` lit `System.getenv("VIGIECHIRO_TOKEN")`, et ce jeton ponctuel
   L EMPORTE sur la connexion enregistree ;
2. les forks surefire HERITENT de l environnement du job - c est ainsi que le job
   `fuseau-alternatif` passe `TZ` ;
3. `ConnexionModule.urlDeBase()` vaut la PRODUCTION par defaut.

La forme juste est donc l `env:` d un **pas**. C est deja celle d `api-live.yml`, et rien ne
l imposait.

## Pourquoi ce garde et pas un role cote plateforme

Une parade structurelle avait ete envisagee : un compte retrograde au role `Lecteur`, pour que le
SERVEUR refuse d ecrire plutot que notre discipline. Elle n existe pas : le role est declare dans
`ROLE_RULES` et **aucune route ne l accepte**. Un jeton de `Lecteur` ne lirait rien. La lecture seule
ne tient donc que par la PORTEE du secret, et une propriete qui ne tient que par la discipline appelle
un garde (ADR 4235).

## Ce qu elle surveille, et pourquoi pas tout

Les secrets dont le nom commence par `VIGIECHIRO_`, et eux seuls. `FLATPAK_GPG_KEY` et `WINGET_TOKEN`
vivent dans des jobs qui n executent aucun test du produit : les inclure ferait rougir la ou le
mecanisme ci-dessus n existe pas, et une regle qui rougit sur du code juste apprend a ne plus lire sa
sortie (ADR 3479). Le prefixe, plutot qu un nom, pour que le second secret du tournage connecte
(#4304) soit couvert le jour ou il naitra, sans qu on ait a y penser.

## Le second controle : `secrets: inherit` (#4349)

Une declaration nominale cote APPELE n achete rien tant que l appelant herite. `release.yml` passait
`secrets: inherit` a trois workflows appeles, dont un qui execute les tests du produit : tous les
secrets du depot lui etaient offerts. Aucun n etait lu ; ce n etait pas une fuite, c etait une
surface. `GITHUB_TOKEN` n entre pas dans ce compte : il est fourni d office a un workflow appele.

## Ce qu elle NE voit PAS, dit ici plutot que suppose

Elle lit du YAML. Un secret qu un `run:` exporterait lui-meme (`echo "X=..." >> "$GITHUB_ENV"`) lui
echappe, et c est un contournement possible. Elle ne juge pas non plus ce que le pas fait du secret
une fois qu il l a : la portee est une condition necessaire, pas une preuve.

Usage : python3 .github/scripts/verifie_portee_des_secrets.py [--auto-test]
"""

from __future__ import annotations

import os
import pathlib
import re
import sys

RACINE = pathlib.Path(__file__).resolve().parents[2]

# Le PREFIXE, et non une liste de noms : le second secret du tournage connecte sera couvert sans
# qu on ait a revenir ici.
SECRET = re.compile(r"secrets\.(VIGIECHIRO_[A-Za-z0-9_]*)")


def exige_pyyaml() -> bool:
    """PyYAML est requis, et son absence se DIT.

    Un YAML de workflow ne se lit pas a la ligne sans se tromper (blocs, ancres, `on:` que YAML
    interprete en booleen). Une garde qui se saute quand son outillage manque est un faux vert de
    plus. Ce controle est appele au point d usage : pose en tete de fichier, il passerait AVANT le
    dispatch des options et rendrait un refus la ou l appelant demandait autre chose (#5008).
    """
    try:
        import yaml  # noqa: F401
    except ImportError:
        print("❌ PyYAML est absent : cette garde ne peut pas lire les workflows.")
        print("   Installer avec « pip install --group gardes », qui lit pyproject.toml.")
        return False
    return True


def secrets_de(valeur) -> list[str]:
    """Les secrets de plateforme cites quelque part sous `valeur`, quelle que soit sa forme."""
    import yaml

    return sorted(set(SECRET.findall(yaml.safe_dump(valeur, allow_unicode=True))))


def verifier(flux: str | pathlib.Path | None = None) -> int:
    """Les deux controles, dans l ordre, et le code de sortie qui va avec."""
    if not exige_pyyaml():
        return 1
    import yaml

    dossier = pathlib.Path(flux or os.environ.get("FLUX", RACINE / ".github" / "workflows"))

    fautives: list[str] = []
    heritages: list[str] = []
    legitimes = 0

    for fichier in sorted(p.name for p in dossier.iterdir() if p.is_file()):
        if not fichier.endswith((".yml", ".yaml")):
            continue
        contenu = yaml.safe_load((dossier / fichier).read_text(encoding="utf-8"))
        if not isinstance(contenu, dict):
            continue

        # Le plancher du workflow : il descend dans TOUS les jobs, donc il est pire qu un `env:` de
        # job.
        for nom in secrets_de(contenu.get("env") or {}):
            fautives.append(f"{fichier} · `env:` du workflow · secrets.{nom}")

        jobs = contenu.get("jobs") or {}
        if not isinstance(jobs, dict):
            continue
        for identifiant, job in jobs.items():
            if not isinstance(job, dict):
                continue
            for nom in secrets_de(job.get("env") or {}):
                fautives.append(f"{fichier} · `env:` du job « {identifiant} » · secrets.{nom}")

            # `secrets: inherit` se lit comme la CHAINE « inherit », la ou la forme juste est une
            # table. C est ce qui permet de les distinguer sans deviner.
            if job.get("secrets") == "inherit":
                heritages.append(f"{fichier} · job « {identifiant} » · secrets: inherit")
            # Tout le reste du job est LEGITIME et doit le rester. Un garde qui refuserait ces
            # formes-la interdirait la seule facon juste de se servir d un secret, et se ferait
            # contourner plutot que corriger.
            pas = job.get("steps") or []
            if isinstance(pas, list):
                for etape in pas:
                    if isinstance(etape, dict):
                        legitimes += len(secrets_de(etape))

    if heritages:
        print(f"✗ {len(heritages)} appel(s) transmettant TOUT le trousseau du dépôt :")
        for h in heritages:
            print(f"   · {h}")
        print()
        print(
            "  `secrets: inherit` passe au workflow appelé TOUS les secrets du dépôt, y compris ceux"
        )
        print(
            "  qu'il ne lit pas et ceux qui naîtront après. Sur un appelé qui exécute les tests du"
        )
        print(
            "  produit, c'est-à-dire du code qui change à chaque PR, la surface est celle du trousseau"
        )
        print("  entier pour la commodité de ne pas écrire trois lignes.")
        print("  Les nommer : `secrets:` suivi de ce dont l'appelé se sert, et de rien d'autre.")
        print(
            "  S'il ne lit que `GITHUB_TOKEN`, il n'y a RIEN à transmettre : celui-là est fourni d'office."
        )
        return 1

    if fautives:
        print(f"✗ {len(fautives)} secret(s) de plateforme posé(s) trop haut :")
        for f in fautives:
            print(f"   · {f}")
        print()
        print(
            "  Un `env:` de job (ou de workflow) offre le secret à TOUT ce que le job exécute, suite de"
        )
        print(
            "  tests comprise. `ConnexionModule` lit `System.getenv`, les forks surefire héritent de"
        )
        print(
            "  l'environnement, et l'URL de base vaut la production : la suite entière serait armée"
        )
        print("  face à la plateforme réelle, et non le seul pas visé.")
        print("  Le descendre dans l'`env:` du pas qui en a besoin.")
        return 1

    print(
        f"✓ Aucun secret de plateforme au-dessus d'un pas ({legitimes} usage(s) de pas relevé(s))."
    )
    return 0


ENV_DU_JOB = """jobs:
  a:
    runs-on: ubuntu-latest
    env:
      VIGIECHIRO_TOKEN: ${{ secrets.VIGIECHIRO_TOKEN }}
    steps:
      - run: ./mvnw test"""
ENV_DU_WORKFLOW = """env:
  VIGIECHIRO_TOKEN: ${{ secrets.VIGIECHIRO_TOKEN }}
jobs:
  a:
    runs-on: ubuntu-latest
    steps:
      - run: ./mvnw test"""
AUTRE_SECRET = """jobs:
  a:
    runs-on: ubuntu-latest
    env:
      VIGIECHIRO_TOKEN_TOURNAGE: ${{ secrets.VIGIECHIRO_TOKEN_TOURNAGE }}
    steps:
      - run: ./mvnw test"""
ENV_DU_PAS = """jobs:
  a:
    runs-on: ubuntu-latest
    steps:
      - env:
          VIGIECHIRO_TOKEN: ${{ secrets.VIGIECHIRO_TOKEN }}
        run: ./mvnw -Papi-live test"""
TRANSMIS_NOMMEMENT = """jobs:
  a:
    uses: ./.github/workflows/appele.yml
    secrets:
      VIGIECHIRO_TOKEN: ${{ secrets.VIGIECHIRO_TOKEN }}"""
HORS_PLATEFORME = """jobs:
  a:
    runs-on: ubuntu-latest
    env:
      GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
      WINGET_TOKEN: ${{ secrets.WINGET_TOKEN }}
    steps:
      - run: gh release list"""
HERITAGE = """jobs:
  a:
    uses: ./.github/workflows/appele.yml
    secrets: inherit"""
SANS_SECRET = """jobs:
  a:
    uses: ./.github/workflows/appele.yml
    with:
      version: v1.0.0"""

CAS = (
    ("un secret de plateforme dans l env d un JOB est refusé", "rouge", ENV_DU_JOB),
    ("dans l env du WORKFLOW, refusé aussi", "rouge", ENV_DU_WORKFLOW),
    # Le prefixe, et non un nom : le second secret du tournage connecte n aura pas le meme nom.
    ("un AUTRE secret de plateforme est couvert par le préfixe", "rouge", AUTRE_SECRET),
    # Controle negatif, et c est le cas qui empeche ce garde de tout refuser : la forme JUSTE.
    ("dans l env d un PAS, la forme juste passe", "vert", ENV_DU_PAS),
    # Second controle negatif : transmettre nommement n est pas une portee de job.
    ("un secret transmis à un workflow appelé passe", "vert", TRANSMIS_NOMMEMENT),
    # Troisieme : la regle ne parle QUE des secrets de plateforme.
    ("un secret hors plateforme au niveau du job passe", "vert", HORS_PLATEFORME),
    # Le controle de #4349 : l appelant qui herite de tout.
    ("un appel qui hérite de TOUT le trousseau est refusé", "rouge", HERITAGE),
    # Et son controle negatif : un appel qui ne transmet RIEN est la forme juste quand l appele ne
    # lit que `GITHUB_TOKEN`. Le refuser aurait pousse a ecrire `inherit` par depit.
    ("un appel qui ne transmet aucun secret passe", "vert", SANS_SECRET),
)


def _auto_test() -> int:
    """Les huit cas de la version bash, dont QUATRE qui doivent rougir."""
    import contextlib
    import io
    import tempfile

    total = echecs = rouges = 0
    print("AUTO-TEST")
    with tempfile.TemporaryDirectory(prefix="vc-portee-") as tmp:
        flux = pathlib.Path(tmp) / ".github" / "workflows"
        flux.mkdir(parents=True)
        for nom, attendu, contenu in CAS:
            (flux / "essai.yml").write_text(contenu + "\n", encoding="utf-8")
            with (
                contextlib.redirect_stdout(io.StringIO()),
                contextlib.redirect_stderr(io.StringIO()),
            ):
                code = verifier(flux)
            obtenu = "vert" if code == 0 else "rouge"
            total += 1
            if attendu == "rouge":
                rouges += 1
            if obtenu == attendu:
                print(f"  [OK   ] {nom:<56} -> {obtenu}")
            else:
                print(f"  [ÉCHEC] {nom:<56} -> {obtenu} (attendu {attendu})")
                echecs += 1

    print()
    print(f"{total} cas, dont {rouges} qui DOIVENT rougir.")
    if echecs != 0:
        print(f"AUTO-TEST EN ÉCHEC ({echecs}) : ne pas se fier au verdict de ce garde.")
        return 1
    print("Auto-test concluant.")
    return 0


if __name__ == "__main__":
    if "--auto-test" in sys.argv[1:2]:
        sys.exit(_auto_test())
    sys.exit(verifier())
