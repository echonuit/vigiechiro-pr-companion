#!/usr/bin/env python3
"""Trois decisions du tournage connecte tiennent dans le YAML (#5221, porte du bash).

Elles ne se tiennent pas par un test : elles vivent dans la forme de deux ateliers, et rien ne les
relisait. Chacune a un cout connu si elle lache.

1. **`comparer-tournages.yml` REFUSE la source `clips-connectes`.** Comparer un tournage connecte a
   un autre mesure la plateforme au lieu du produit, et rend un chiffre qui a l air juste (#4306).
   Ce refus s eprouve en **executant** le bloc, pas en le lisant.
2. **`publier-connecte` depend de `filmer` et porte une fonction d etat.** Sans la fonction, GitHub
   enveloppe la condition en `success() && (...)` sur TOUT le graphe amont : la porte qu on croit
   avoir ecrite n est jamais evaluee, rien ne rougit, et le job est simplement saute.
3. **Le controle du jeton vient AVANT le pas qui filme**, et reste garde par `inputs.connecte`.
   Sonder apres avoir filme ne coute rien et ne sert a rien ; sonder sans la garde refuserait tout
   tournage hors ligne, qui n a pas de jeton et n en veut pas.

## Le leurre pour `gh`, et pourquoi le verdict se prend sur le MESSAGE

La premiere decision s eprouve en lancant le bloc extrait de l atelier, avec un `gh` qui echoue
toujours : le refus doit tomber **avant** tout appel reseau. Le leurre faisant echouer toutes les
sources, un verdict pris sur le code de sortie serait vert quoi qu il arrive - c est donc la phrase
« ne se compare pas » qui est exigee, et son absence sur une source ORDINAIRE avec elle.

## La seule sortie que le portage ne reproduit pas au caractere pres

L alignement des libelles de l auto-test. Le `printf '%-62s'` du shell remplit jusqu a 62 **octets**,
si bien qu un libelle accentue etait sous-rempli et que la colonne des verdicts sautait d une ligne a
l autre ; `{:<62}` compte des **caracteres**, et la colonne est droite. Tout le reste - les verdicts,
les codes de sortie, les messages de refus - sort a l identique, et rien ne lit cet alignement.
"""

from __future__ import annotations

import os
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile

RACINE = pathlib.Path(__file__).resolve().parents[2]
ETAT = re.compile(r"\b(always|success|failure|cancelled)\s*\(\s*\)")


def _charge(chemin: pathlib.Path):
    import yaml

    return yaml.safe_load(chemin.read_text(encoding="utf-8"))


def refus_de_la_source_connectee(flux: pathlib.Path) -> bool:
    """La premiere decision, eprouvee en EXECUTANT le bloc de l atelier."""
    blocs = [
        e["run"]
        for j in (_charge(flux / "comparer-tournages.yml")["jobs"]).values()
        for e in j.get("steps", [])
        if "reprendre()" in e.get("run", "")
    ]
    if len(blocs) != 1:
        print(
            f"❌ {len(blocs)} pas définissent `reprendre()` dans comparer-tournages.yml, attendu 1."
        )
        print("   La forme a changé : ce garde ne sait plus quoi lancer, et il le dit plutôt que")
        print("   de rendre un vert qui ne vaudrait rien.")
        return False

    with tempfile.TemporaryDirectory(prefix="vc-conn-") as tmp:
        bac = pathlib.Path(tmp)
        # Un leurre pour `gh` : le refus doit tomber AVANT tout appel reseau, et si un jour il
        # tombe apres, on veut le voir ici plutot qu en production.
        (bac / "bin").mkdir()
        leurre = bac / "bin" / "gh"
        leurre.write_text("#!/usr/bin/env bash\nexit 1\n", encoding="utf-8")
        leurre.chmod(0o755)
        bloc = bac / "reprendre.sh"
        bloc.write_text(blocs[0], encoding="utf-8")

        def lance(avant: str, apres: str) -> str:
            env = dict(os.environ)
            env["PATH"] = f"{bac / 'bin'}{os.pathsep}{env.get('PATH', '')}"
            env["AVANT"], env["APRES"] = avant, apres
            rendu = subprocess.run(
                ["bash", str(bloc)], capture_output=True, text=True, cwd=bac, env=env, check=False
            )
            return rendu.stdout + rendu.stderr

        if "ne se compare pas" not in lance("clips-connectes", "v1.0.0"):
            print("❌ comparer-tournages.yml n a pas refusé la source « clips-connectes ».")
            print(
                "   Ce refus EST un garde : sans lui, la comparaison mesure la plateforme au lieu"
            )
            print("   du produit et rend un chiffre qui a l air juste (#4306).")
            return False
        # La plateforme de test, elle, n est PAS refusee (#5793, ADR 5641) : son etat de depart est
        # declare, et la condition de sa comparaison se mesure (#5797), elle ne se refuse pas.
        if "ne se compare pas" in lance("clips-plateforme-de-test", "v1.0.0"):
            print("❌ comparer-tournages.yml refuse la source « clips-plateforme-de-test ».")
            print("   L ADR 5641 leve ce refus pour la plateforme de test : il ne vaut que pour la")
            print("   plateforme nationale, dont l ecran suit des donnees vivantes.")
            return False
        # Le controle de l autre bord : une source ordinaire ne doit PAS declencher ce refus.
        if "ne se compare pas" in lance("v2.186.0", "v2.187.0"):
            print(
                "❌ comparer-tournages.yml refuse AUSSI une source ordinaire : le refus ne "
                "discrimine plus."
            )
            return False
    return True


def versement_conditionne(flux: pathlib.Path) -> bool:
    """La deuxieme : chaque job qui verse des clips depend de `filmer` et porte une fonction d etat.

    `publier-connecte` depuis #4306, `publier-plateforme-de-test` depuis #5793 : un tournage ampute
    n a pas a se publier, ni sur l une ni sur l autre pre-version.
    """
    f = _charge(flux / "tournage-recette.yml")
    tiennent = True
    for nom in ("publier-connecte", "publier-plateforme-de-test"):
        job = f["jobs"].get(nom)
        if job is None:
            print(f"❌ Le job `{nom}` a disparu de tournage-recette.yml.")
            tiennent = False
            continue
        besoins = job.get("needs") or []
        if isinstance(besoins, str):
            besoins = [besoins]
        if "filmer" not in besoins:
            print(f"❌ `{nom}` ne dépend plus de `filmer` : un tournage amputé pourrait publier.")
            tiennent = False
        condition = str(job.get("if", ""))
        if not ETAT.search(condition):
            print(f"❌ La condition de `{nom}` ne porte aucune fonction d'état :")
            print(f"     if: {condition or '(absente)'}")
            print(
                "   Le « sauté » se propage transitivement : cette porte ne serait jamais évaluée."
            )
            tiennent = False
    return tiennent


def controle_avant_le_tournage(flux: pathlib.Path) -> bool:
    """La troisieme : le jeton se controle AVANT de filmer, et seulement en connecte."""
    f = _charge(flux / "tournage-recette.yml")
    pas = f["jobs"]["filmer"]["steps"]
    noms = [str(e.get("name", "")) for e in pas]
    sondes = [i for i, n in enumerate(noms) if re.search(r"jeton.*vivant", n, re.I)]
    tournages = [i for i, n in enumerate(noms) if n.strip() == "Filmer"]
    if not sondes:
        print("❌ Aucun pas ne contrôle le jeton dans le job `filmer`.")
        return False
    if not tournages:
        print(
            "❌ Le pas « Filmer » a disparu du job `filmer` : ce garde ne sait plus par rapport à"
        )
        print("   quoi juger l'ordre, et il le dit plutôt que de rendre un vert vide.")
        return False
    if min(sondes) > min(tournages):
        print(
            f"❌ Le contrôle du jeton vient APRÈS le pas qui filme "
            f"({min(tournages) + 1} puis {min(sondes) + 1})."
        )
        print(
            "   Sonder après avoir filmé ne coûte rien et ne sert à rien : le clip hors ligne est"
        )
        print("   déjà tourné quand on apprend que le jeton était mort.")
        return False
    condition = str(pas[min(sondes)].get("if", ""))
    if "inputs.connecte" not in condition:
        print("❌ Le contrôle du jeton n'est plus gardé par `inputs.connecte` :")
        print(f"     if: {condition or '(absente)'}")
        print(
            "   Il refuserait alors tout tournage hors ligne, qui n'a pas de jeton et n'en veut pas."
        )
        return False
    return True


def verdict(flux: pathlib.Path) -> bool:
    """Les trois, et le verdict d ensemble. Chacune s exprime, meme si une precedente a lache."""
    tiennent = [
        refus_de_la_source_connectee(flux),
        versement_conditionne(flux),
        controle_avant_le_tournage(flux),
    ]
    return all(tiennent)


def _casse_le_refus(dossier: pathlib.Path) -> None:
    p = dossier / "comparer-tournages.yml"
    t = p.read_text(encoding="utf-8")
    p.write_text(
        t.replace(
            '            if [ "$source" = "clips-connectes" ]; then',
            "            if false; then",
            1,
        ),
        encoding="utf-8",
    )


def _casse_la_fonction_d_etat(dossier: pathlib.Path) -> None:
    p = dossier / "tournage-recette.yml"
    t = p.read_text(encoding="utf-8")
    p.write_text(
        t.replace(
            "    if: ${{ success() && inputs.connecte && needs.revoquer.outputs.revoque == 'oui' }}",
            "    if: ${{ inputs.connecte && needs.revoquer.outputs.revoque == 'oui' }}",
            1,
        ),
        encoding="utf-8",
    )


def _refuse_aussi_la_plateforme_de_test(dossier: pathlib.Path) -> None:
    p = dossier / "comparer-tournages.yml"
    t = p.read_text(encoding="utf-8")
    p.write_text(
        t.replace(
            '            if [ "$source" = "clips-connectes" ]; then',
            '            if [ "$source" = "clips-connectes" ] || [ "$source" = "clips-plateforme-de-test" ]; then',
            1,
        ),
        encoding="utf-8",
    )


def _casse_la_fonction_d_etat_de_test(dossier: pathlib.Path) -> None:
    p = dossier / "tournage-recette.yml"
    t = p.read_text(encoding="utf-8")
    p.write_text(
        t.replace(
            "    if: ${{ success() && inputs.plateforme_de_test }}",
            "    if: ${{ inputs.plateforme_de_test }}",
            1,
        ),
        encoding="utf-8",
    )


def _deplace_la_sonde(dossier: pathlib.Path) -> None:
    import yaml

    p = dossier / "tournage-recette.yml"
    f = yaml.safe_load(p.read_text(encoding="utf-8"))
    pas = f["jobs"]["filmer"]["steps"]
    i = next(n for n, e in enumerate(pas) if "vivant" in str(e.get("name", "")))
    j = next(n for n, e in enumerate(pas) if str(e.get("name", "")).strip() == "Filmer")
    pas.insert(j + 1, pas.pop(i))
    with p.open("w", encoding="utf-8") as sortie:
        yaml.safe_dump(f, sortie, allow_unicode=True, sort_keys=False)


# Chaque cassure retire UNE decision, et rien d autre. C est la ou ce fichier gagne son verdict.
CASSURES = (
    (_casse_le_refus, "le refus de clips-connectes neutralisé"),
    (_casse_la_fonction_d_etat, "publier-connecte privé de sa fonction d état"),
    (_deplace_la_sonde, "le contrôle du jeton déplacé après le tournage"),
    (_refuse_aussi_la_plateforme_de_test, "refus étendu à clips-plateforme-de-test"),
    (_casse_la_fonction_d_etat_de_test, "publier-plateforme-de-test privé de sa fonction d état"),
)


def _auto_test() -> int:
    """Les workflows sains, puis une cassure par decision gardee."""
    import contextlib
    import io

    total = echecs = 0
    print("AUTO-TEST")

    def essai(dossier: pathlib.Path, attendu: str, libelle: str) -> None:
        nonlocal total, echecs
        total += 1
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            ok = verdict(dossier)
        obtenu = "vert" if ok else "rouge"
        if obtenu == attendu:
            print(f"  [OK   ] {libelle:<62} -> {obtenu}")
        else:
            print(f"  [ÉCHEC] {libelle:<62} -> {obtenu} (attendu {attendu})")
            echecs += 1

    with tempfile.TemporaryDirectory(prefix="vc-dec-") as tmp:
        bac = pathlib.Path(tmp)
        sain = bac / "sain"
        sain.mkdir()
        for nom in ("tournage-recette.yml", "comparer-tournages.yml"):
            shutil.copy(RACINE / ".github" / "workflows" / nom, sain / nom)
        # `sain` doit etre VERT, sinon tout le reste ment.
        essai(sain, "vert", "les workflows tels qu ils sont")

        for casse, libelle in CASSURES:
            dossier = bac / libelle.split()[0]
            dossier.mkdir(exist_ok=True)
            for f in sain.glob("*.yml"):
                shutil.copy(f, dossier / f.name)
            casse(dossier)
            essai(dossier, "rouge", libelle)

    print()
    print(f"{total} cas : les workflows sains, puis une cassure par décision gardée.")
    if echecs:
        print(f"AUTO-TEST EN ÉCHEC ({echecs}) : ne pas se fier au verdict de ce script.")
        return 1
    print("Auto-test concluant.")
    return 0


if __name__ == "__main__":
    if "--auto-test" in sys.argv:
        sys.exit(_auto_test())
    flux = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else RACINE / ".github" / "workflows"
    if verdict(flux):
        print(
            "✓ Les trois décisions du tournage connecté tiennent : refus de clips-connectes,"
            " versement"
        )
        print("  conditionné, contrôle du jeton avant le tournage.")
        sys.exit(0)
    print("::error::Une décision du tournage connecté n est plus tenue par le YAML, cf. ci-dessus.")
    sys.exit(1)
