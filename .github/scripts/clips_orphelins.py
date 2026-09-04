#!/usr/bin/env python3
"""Les clips de la pre-version roulante que le DERNIER tournage n a pas produits (#5229, porte du bash).

Pourquoi ce calcul vit dans un script, et pas en trois lignes de YAML. Sa sortie sert a **supprimer**
des pieces publiees. Une erreur d orientation ne rend pas une erreur : elle rend l exact contraire,
la liste de ce qu il fallait garder, et le job la supprime sans rien dire. Un script se met a
l epreuve ; une ligne de `run:` ne s eprouve qu en production.

## Le defaut qu il ferme

`gh release upload --clobber` remplace et ajoute ; il ne retire rien. Un cas de recette renomme ou
supprime laisse donc son clip en ligne, et ce clip continue de montrer un comportement que le produit
n a plus. Constate apres #4099, qui a renomme deux cas : leurs deux anciens clips sont restes
telechargeables, montrant une fenetre de compte rendu supprimee depuis #4091.

## Ce qu il refuse

Un dossier de tournage **vide**. Le tournage filme toutes les classes citant un cas : s il n a rien
produit, c est le tournage qui a echoue, et non tous les cas qui ont disparu. Designer alors la
pre-version entiere serait la panne la plus couteuse de ce script, et la plus silencieuse.

## L orientation, et le cas qui la garde

`comm -13` rendait ce qui est EN LIGNE sans etre TOURNE ; ici, `enligne - tournes`. Prise a l envers,
la difference designerait les clips fraichement tournes, c est-a-dire exactement ceux qu il faut
garder. Un cas d auto-test tient ce sens, et il est le seul a le tenir.

Usage :
  clips_orphelins.py <dossier-des-clips-tournes>   # la liste EN LIGNE arrive sur stdin
  clips_orphelins.py --auto-test

Sortie : un nom de clip orphelin par ligne, sur stdout. Exit 0 s il y en a ou non, 2 sur refus.
"""

from __future__ import annotations

import pathlib
import sys


def orphelins(dossier: str | pathlib.Path, enligne_brut: str) -> tuple[list[str], int]:
    """Les clips en ligne qu aucun clip tourne ne couvre, et le code de sortie qui va avec."""
    base = pathlib.Path(dossier)
    tournes = sorted(p.name for p in base.glob("*.mp4")) if base.is_dir() else []

    if not tournes:
        print(f"REFUS : aucun clip .mp4 dans « {dossier} ».", file=sys.stderr)
        print(
            "Un tournage qui n'a rien produit ne doit pas conduire à vider la pré-version.",
            file=sys.stderr,
        )
        return [], 2

    # Seuls les .mp4 sont concernes : l index de la pre-version n est pas un clip.
    enligne = sorted({l for l in enligne_brut.splitlines() if l.endswith(".mp4")})
    return [c for c in enligne if c not in set(tournes)], 0


# (nom, clips tournes, liste en ligne, sortie attendue)
CAS = (
    ("rien à retirer quand la pré-version colle au tournage", "a.mp4 b.mp4", "a.mp4 b.mp4", ""),
    (
        "un cas renommé laisse son ancien clip, qui est désigné",
        "a.mp4 nouveau.mp4",
        "a.mp4 ancien.mp4 nouveau.mp4",
        "ancien.mp4",
    ),
    ("l'index de la pré-version n'est jamais désigné", "a.mp4", "a.mp4 index.md", ""),
    # Le cas qui garde l ORIENTATION. Prise a l envers, la difference designerait ce clip
    # fraichement tourne mais pas encore en ligne - donc exactement ce qu il faut garder.
    (
        "un clip tourné mais absent de la pré-version n'est pas désigné",
        "a.mp4 tout-neuf.mp4",
        "a.mp4",
        "",
    ),
    # Le cas le plus couteux : un tournage qui n a rien produit ne vide pas la pre-version.
    ("un dossier de tournage vide est refusé", "", "a.mp4 b.mp4", "REFUS"),
)


def _auto_test() -> int:
    """Les cinq cas de la version bash, dont celui qui garde l orientation."""
    import contextlib
    import io
    import tempfile

    total = echecs = 0
    print("AUTO-TEST")
    with tempfile.TemporaryDirectory(prefix="vc-clips-") as tmp:
        for nom, tournes, enligne, attendu in CAS:
            clips = pathlib.Path(tmp) / "clips"
            if clips.is_dir():
                for f in clips.iterdir():
                    f.unlink()
            clips.mkdir(exist_ok=True)
            for clip in tournes.split():
                (clips / clip).touch()
            # Le refus ecrit sur la sortie d erreur, et le cas qui l attend juge le RESULTAT :
            # la laisser passer melerait le message du cas refuse aux verdicts des autres.
            with contextlib.redirect_stderr(io.StringIO()):
                designes, code = orphelins(clips, "\n".join(enligne.split()))
            obtenu = "REFUS" if code == 2 else "\n".join(designes)
            total += 1
            if obtenu == attendu:
                print(f"  [OK   ] {nom:<56} -> {obtenu or '（vide）'}")
            else:
                print(
                    f"  [ÉCHEC] {nom:<56} -> {obtenu or '（vide）'} (attendu {attendu or '（vide）'})"
                )
                echecs += 1

    print(f"  {total - echecs}/{total}")
    return 1 if echecs else 0


if __name__ == "__main__":
    argument = sys.argv[1] if len(sys.argv) > 1 else ""
    if argument == "--auto-test":
        sys.exit(_auto_test())
    if argument == "":
        print(
            f"usage : {sys.argv[0]} <dossier-des-clips-tournes>   (liste en ligne sur stdin)",
            file=sys.stderr,
        )
        sys.exit(64)
    designes, code = orphelins(argument, sys.stdin.read())
    for c in designes:
        print(c)
    sys.exit(code)
