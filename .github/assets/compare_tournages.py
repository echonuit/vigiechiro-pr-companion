#!/usr/bin/env python3
"""Dit ce qui a change entre DEUX tournages de recette - et le dit aussi quand rien n a change.

Porte du bash en #5239.

## Le probleme

Depuis #4269, chaque version porte les clips de ses deux bancs sur son tag. Savoir ce qui a bouge
entre la derniere version et le tournage courant demandait d ouvrir cinquante lecteurs et de se
souvenir. Ce script fait le tri ; le regard fait le reste.

## Ce qu il produit

Un dossier d images et un index Markdown. Par cas, trois signaux, du plus fiable au moins fiable : la
PRESENCE - le cas est dans les deux tournages, ou apparu, ou disparu ; l IMAGE FINALE - les deux
dernieres images accolees, leur carte des differences, et la part de pixels changes ; la DUREE - un
scenario qui s allonge a presque toujours change.

## Pourquoi la derniere image, et pas une image au milieu

Deux tournages ne se deroulent pas au meme rythme : comparer l image n°40 de l un a l image n°40 de
l autre compare deux instants differents. A la fin, le scenario est POSE, et c est le seul moment ou
les deux sont comparables sans dependre de leur cadence.

**Contrepartie assumee : on compare la destination, pas le chemin.** Un cas dont l objet est une
transition garderait une fin identique alors que son milieu aurait bouge. La duree est le seul
garde-fou bon marche contre ca, et il est grossier.

## Pourquoi une tolerance de couleur, et pourquoi elle se mesure

Mesure sur deux tournages du MEME commit (#4274) : l ecart brut monte a **16 %** sur un cas, et il est
entierement du a l ANTICRENELAGE. Avec `-fuzz 5%`, ce meme plancher tombe sous **0,01 %**, et
l instrument n en devient pas aveugle : un chiffre change rend 0,021 %, un mot 0,101 %, un encart
4,2 %. `--plancher` remesure le plancher sur place : une tolerance figee finirait par mentir.

## Le plancher se mesure par CAS, pas une fois pour toutes

Mesure sur 51 cas, deux tournages du meme commit sur deux runners : la mediane du plancher vaut
0,008 %, 48 cas sur 51 sont sous 0,05 %, et TROIS depassent - jusqu a 0,809 %. Un seuil unique
mentirait donc dans les deux sens (#4287).

Usage : python3 .github/assets/compare_tournages.py <avant> <après> <sortie> [tolérance %] [planchers]
        python3 .github/assets/compare_tournages.py --plancher <A> <B> [fichier de planchers]
        python3 .github/assets/compare_tournages.py --auto-test
"""

from __future__ import annotations

import pathlib
import shutil
import subprocess
import sys
import tempfile

ICI = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(ICI))

# La mesure des pixels est PARTAGEE avec l autre comparaison : elle portait le meme defaut aux deux
# endroits, corrige deux fois (#4295).
from mesure_pixels import INCONNUE, part_changee

TOLERANCE_PAR_DEFAUT = 5
OUTILS = ("ffmpeg", "ffprobe", "compare", "convert", "identify")


def exige_ses_outils() -> bool:
    """Refuse de commencer sans ses outils, en NOMMANT ceux qui manquent.

    Sans cette garde, l absence d un outil ne se voyait pas : `compare` introuvable ecrivait son
    « command not found » dans la sortie que la mesure capture, la part de pixels devenait « ? », et
    les cinquante cas d un vrai tournage se rangeaient en « mesure impossible » sans qu une seule
    ligne ne dise POURQUOI. Mesure sur le run 32640637929, ou le job restait vert.

    Un outil manquant est une PANNE D INSTALLATION, pas un resultat de mesure.
    """
    manquants = [o for o in OUTILS if shutil.which(o) is None]
    if not manquants:
        return True
    print(
        f"::error::Outils absents : {' '.join(manquants)}. Une mesure impossible faute d'outil n'est pas un",
        file=sys.stderr,
    )
    print("::error::résultat : installer ffmpeg et imagemagick avant de comparer.", file=sys.stderr)
    return False


def derniere_image(clip: pathlib.Path, sortie: pathlib.Path) -> bool:
    """`-update 1` reecrit le meme fichier a chaque image : ce qui reste est la derniere."""
    return (
        subprocess.run(
            [
                "ffmpeg",
                "-v",
                "error",
                "-y",
                "-i",
                str(clip),
                "-vsync",
                "0",
                "-f",
                "image2",
                "-update",
                "1",
                str(sortie),
            ],
            capture_output=True,
            check=False,
        ).returncode
        == 0
    )


def premiere_image(clip: pathlib.Path, sortie: pathlib.Path) -> bool:
    """La PREMIERE image d un clip.

    Mesuree sur 51 cas : son plancher vaut **0,000 % partout**, quand celui de la derniere image
    monte a 0,809 % sur son pire cas. Stable ne suffisait pas - une mesure toujours nulle peut aussi
    etre AVEUGLE. Verifie : les premieres images de deux cas differents different de 2,4 a 3 % (#4296).
    """
    return (
        subprocess.run(
            ["ffmpeg", "-v", "error", "-y", "-i", str(clip), "-frames:v", "1", str(sortie)],
            capture_output=True,
            check=False,
        ).returncode
        == 0
    )


def duree(clip: pathlib.Path) -> str:
    """La duree d un clip en secondes, ou « ? » si elle ne se lit pas."""
    rendu = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(clip)],
        capture_output=True,
        text=True,
        check=False,
    )
    if rendu.returncode != 0 or not rendu.stdout.strip():
        return INCONNUE
    try:
        return f"{float(rendu.stdout.strip()):.1f}"
    except ValueError:
        return INCONNUE


def rapport_de(ecart: str, plancher: str) -> str:
    """Le rapport d un ecart au plancher de son cas, ou « ? » si ce plancher n est pas connu.

    Le plancher est borne par le bas : un plancher nul diviserait par zero, et le borner a 0,001
    revient a dire « au moins un millieme de pourcent », deja sous le plus petit ecart mesurable.
    """
    if not plancher:
        return INCONNUE
    sol = float(plancher)
    return f"{float(ecart) / (max(0.001, sol)):.1f}"


def cas_du_dossier(dossier: pathlib.Path) -> list[str]:
    """Les noms de cas d un dossier de clips, tries."""
    return sorted(p.stem for p in pathlib.Path(dossier).glob("*.mp4"))


def _lit_les_planchers(fichier: pathlib.Path) -> tuple[dict, dict, dict]:
    """Les trois tables du fichier de planchers, ancien format compris.

    Quatre colonnes depuis #4296 : cas, plancher du DEBUT, plancher de la FIN, paires. Un fichier a
    trois colonnes est l ancien format - le plancher qu il porte est celui de la fin, et celui du
    debut reste inconnu plutot que d etre suppose nul.
    """
    sol_deb, sol_fin, nbp = {}, {}, {}
    if not fichier.is_file():
        return sol_deb, sol_fin, nbp
    for ligne in fichier.read_text(encoding="utf-8").splitlines():
        if not ligne or ligne.startswith("#"):
            continue
        champs = ligne.split("\t")
        nom = champs[0]
        if len(champs) >= 4 and champs[3]:
            sol_deb[nom], sol_fin[nom], nbp[nom] = champs[1], champs[2], champs[3]
        else:
            sol_deb[nom] = ""
            sol_fin[nom] = champs[1] if len(champs) > 1 else ""
            nbp[nom] = champs[2] if len(champs) > 2 and champs[2] else "1"
    return sol_deb, sol_fin, nbp


def _positif(valeur: str) -> bool:
    try:
        return float(valeur) > 0
    except ValueError:
        return False


def comparer(
    avant_dir: str | pathlib.Path,
    apres_dir: str | pathlib.Path,
    sortie: str | pathlib.Path,
    tolerance: int | str = TOLERANCE_PAR_DEFAUT,
    planchers: str = "",
) -> int:
    """Le dossier d images et l index, et le code de sortie qui va avec."""
    avant_dir, apres_dir, dossier = map(pathlib.Path, (avant_dir, apres_dir, sortie))

    sol_deb: dict[str, str] = {}
    sol_fin: dict[str, str] = {}
    nbp: dict[str, str] = {}
    if planchers:
        # Un fichier ANNONCE mais absent ne se lit pas comme « aucun plancher connu » : c est une
        # erreur de chemin, et sans ce message les cinquante cas diraient tous « plancher inconnu ».
        if not pathlib.Path(planchers).is_file():
            print(f"::error::Fichier de planchers introuvable : « {planchers} ».")
            return 1
        sol_deb, sol_fin, nbp = _lit_les_planchers(pathlib.Path(planchers))

    dossier.mkdir(parents=True, exist_ok=True)
    index = dossier / "index.md"
    noms = sorted(set(cas_du_dossier(avant_dir)) | set(cas_du_dossier(apres_dir)))

    # Aucun clip des deux cotes n est PAS un resultat : c est une comparaison qui n a rien eu a
    # comparer, et les deux se lisent pareil si on ne le dit pas.
    if not noms:
        index.write_text(
            "### Comparaison de deux tournages\n"
            "\n"
            "⚠️ **Aucun clip dans l'un ni l'autre des deux tournages.** Ce n'est pas « rien n'a changé »,\n"
            "c'est « il n'y avait rien à comparer » : vérifier que les deux dossiers ont bien été remplis.\n",
            encoding="utf-8",
        )
        print("::error::Aucun clip à comparer.")
        return 1

    lignes: list[tuple[float, str, str, str, str]] = []
    communs = apparus = disparus = bouges = illisibles = au_dessus = 0

    for nom in noms:
        avant, apres = avant_dir / f"{nom}.mp4", apres_dir / f"{nom}.mp4"

        if not avant.is_file():
            lignes.append((9000000.0, "9000000", nom, "cas **apparu**", "pas d'avant à montrer"))
            apparus += 1
            continue
        if not apres.is_file():
            lignes.append(
                (
                    8000000.0,
                    "8000000",
                    nom,
                    "cas **disparu**",
                    "plus de clip dans le tournage courant",
                )
            )
            disparus += 1
            continue

        communs += 1
        fa, fb = dossier / f"{nom}.fin-a.png", dossier / f"{nom}.fin-b.png"
        da_, db_ = dossier / f"{nom}.deb-a.png", dossier / f"{nom}.deb-b.png"
        if not (
            derniere_image(avant, fa)
            and derniere_image(apres, fb)
            and premiere_image(avant, da_)
            and premiere_image(apres, db_)
        ):
            lignes.append((7000000.0, "7000000", nom, "⚠️ image illisible", "clip corrompu ?"))
            illisibles += 1
            for f in (fa, fb, da_, db_):
                f.unlink(missing_ok=True)
            continue

        p_fin = part_changee(fa, fb, tolerance, 3)
        p_deb = part_changee(da_, db_, tolerance, 3)
        da, db = duree(avant), duree(apres)

        subprocess.run(
            ["convert", str(fa), str(fb), "+append", str(dossier / f"{nom}.avant-apres.png")],
            capture_output=True,
            check=False,
        )
        subprocess.run(
            [
                "compare",
                str(fa),
                str(fb),
                "-highlight-color",
                "red",
                "-lowlight-color",
                "white",
                str(dossier / f"{nom}.ou.png"),
            ],
            capture_output=True,
            check=False,
        )
        # Les fichiers ne repetent PAS le nom du cas : il est deja en premiere colonne, et le
        # repeter faisait des lignes de trois cents caracteres que personne ne lit (passe 8).
        geste = "montage + carte"

        # Les images du DEBUT ne sont produites que s il a bouge. Mesure : son plancher vaut 0,000 %
        # sur les 51 cas, donc en temps normal ce montage serait cinquante fichiers identiques.
        if p_deb != INCONNUE and _positif(p_deb):
            subprocess.run(
                [
                    "convert",
                    str(da_),
                    str(db_),
                    "+append",
                    str(dossier / f"{nom}.debut.avant-apres.png"),
                ],
                capture_output=True,
                check=False,
            )
            subprocess.run(
                [
                    "compare",
                    str(da_),
                    str(db_),
                    "-highlight-color",
                    "red",
                    "-lowlight-color",
                    "white",
                    str(dossier / f"{nom}.debut.ou.png"),
                ],
                capture_output=True,
                check=False,
            )
            geste = f"{geste} · **+ début**"
        for f in (fa, fb, da_, db_):
            f.unlink(missing_ok=True)

        # Une mesure qui ECHOUE ne se range pas parmi les cas qui ne bougent pas. Ce defaut a ete
        # commis en ecrivant ce script : les sept mesures rendaient « ? », et l index annoncait
        # tranquillement « aucun cas ne bouge » (ADR 2748).
        if p_fin == INCONNUE or p_deb == INCONNUE:
            lignes.append(
                (6000000.0, "6000000", nom, "⚠️ mesure impossible", f"{da} s → {db} s · {geste}")
            )
            illisibles += 1
            continue

        if not planchers:
            cellule = f"début {p_deb} % · fin {p_fin} %" if _positif(p_deb) else f"fin {p_fin} %"
            cle = p_deb if float(p_deb) > float(p_fin) else p_fin
        else:
            r_fin = rapport_de(p_fin, sol_fin.get(nom, ""))
            r_deb = rapport_de(p_deb, sol_deb.get(nom, ""))
            if r_fin == INCONNUE and r_deb == INCONNUE:
                # Un cas sans plancher connu se DIT : le prendre pour stable serait inventer une mesure.
                cellule = f"fin {p_fin} % · ⚠️ plancher inconnu"
                cle = p_deb if float(p_deb) > float(p_fin) else p_fin
            else:
                x = 0.0 if r_deb == INCONNUE else float(r_deb)
                y = 0.0 if r_fin == INCONNUE else float(r_fin)
                cle = f"{max(x, y):.1f}"
                paires = nbp.get(nom, "")
                if _positif(p_deb):
                    cellule = f"**×{cle}** · début {p_deb} % (×{r_deb}) · fin {p_fin} % (×{r_fin}) · {paires} paire(s)"
                else:
                    cellule = f"**×{cle}** · fin {p_fin} % · {paires} paire(s)"
                if float(cle) > 1:
                    au_dessus += 1

        lignes.append((float(cle), cle, nom, cellule, f"{da} s → {db} s · {geste}"))
        # Le comptage se fait sur une comparaison NUMERIQUE, pas sur la chaine : « 10.000 » est plus
        # grand que « 9.000 », et un tri de texte dirait l inverse.
        if _positif(p_deb) or _positif(p_fin):
            bouges += 1

    corps = [
        "### Comparaison de deux tournages",
        "",
        f"Tolérance de couleur : **{tolerance} %**. Le chiffre **trie**, il ne prouve pas :",
        "sous un mot changé on est à deux fois le plancher de bruit. C'est la carte `.ou.png` qui dit **où**.",
        "",
        "⚠️ La **première** image de chaque clip est comparée elle aussi, pour tous les cas. Elle",
        "n'apparaît en ligne que si elle a bougé : son plancher vaut 0,000 % sur 102 mesures, donc",
        "l'afficher partout n'écrirait que des zéros. Son silence veut dire « vérifiée et stable »,",
        "pas « pas regardée ».",
    ]
    if planchers:
        corps += [
            "",
            "Les cas sont classés par leur **rapport au bruit de leur propre cas**, et non par leur écart",
            "absolu : un cas dont le plancher est haut doit bouger davantage pour dire quelque chose.",
            "⚠️ Lire le nombre de paires : un plancher tiré d'une seule paire ne prouve pas la stabilité.",
        ]
    corps.append("")
    if bouges == 0 and apparus == 0 and disparus == 0 and illisibles == 0:
        corps += [
            f"**Aucun cas ne bouge** : les {communs} cas communs rendent une première ET une dernière",
            "image identiques, à la tolérance près, et aucun cas n'est apparu ni disparu.",
        ]
    else:
        corps += ["| Cas | Début et fin | Durée et images |", "|---|---|---|"]
        # `sort -rn -k1,1` : la cle est numerique, MAIS a cle egale GNU sort compare la ligne
        # ENTIERE en dernier ressort, et `-r` renverse cela aussi. Deux cas apparus portent la meme
        # cle 9000000 : sans ce second critere, l ordre de l index differait de celui du bash, et
        # aucun cas d auto-test ne l aurait dit - aucun n a deux ex aequo.
        for _, cle, nom, part, reste in sorted(
            lignes, key=lambda l: (l[0], "\t".join(l[1:])), reverse=True
        ):
            corps.append(f"| `{nom}` | {part} | {reste} |")
    corps.append("")
    if planchers:
        corps += [
            f"_{communs} cas comparé(s), {au_dessus} au-dessus de leur propre plancher, {bouges} au-dessus",
            f"de zéro, {apparus} apparu(s), {disparus} disparu(s), {illisibles} mesure(s) impossible(s)._",
        ]
    else:
        corps.append(
            f"_{communs} cas comparé(s), {bouges} au-dessus de zéro, {apparus} apparu(s), "
            f"{disparus} disparu(s), {illisibles} mesure(s) impossible(s)._"
        )
    index.write_text("\n".join(corps) + "\n", encoding="utf-8")

    if planchers:
        print(
            f"{communs} cas comparé(s), {au_dessus} au-dessus de leur plancher, {bouges} qui bougent, "
            f"{apparus} apparu(s), {disparus} disparu(s), {illisibles} mesure(s) impossible(s)."
        )
    else:
        print(
            f"{communs} cas comparé(s), {bouges} qui bougent, {apparus} apparu(s), "
            f"{disparus} disparu(s), {illisibles} mesure(s) impossible(s)."
        )
    return 0


def plancher(a: str | pathlib.Path, b: str | pathlib.Path, fichier: str = "") -> int:
    """Remesure le plancher : deux tournages qu on SAIT identiques.

    Avec un fichier, ecrit le plancher PAR CAS et l ACCUMULE : relancer sur une autre paire garde le
    PIRE plancher observe et compte une paire de plus. Le pire, et non la moyenne : un plancher qui
    sous-estime le bruit fabrique des faux positifs. Le compte de paires est ecrit parce qu un
    plancher tire d UNE paire ne prouve rien (#4287).
    """
    a, b = pathlib.Path(a), pathlib.Path(b)
    pire = "0"
    sol_deb, sol_fin, nbp = _lit_les_planchers(pathlib.Path(fichier)) if fichier else ({}, {}, {})

    with tempfile.TemporaryDirectory(prefix="vc-plancher-") as tmp:
        bac = pathlib.Path(tmp)
        for nom in cas_du_dossier(a):
            if not (b / f"{nom}.mp4").is_file():
                continue
            if not (
                derniere_image(a / f"{nom}.mp4", bac / "fa.png")
                and derniere_image(b / f"{nom}.mp4", bac / "fb.png")
                and premiere_image(a / f"{nom}.mp4", bac / "da.png")
                and premiere_image(b / f"{nom}.mp4", bac / "db.png")
            ):
                continue

            brut = part_changee(bac / "fa.png", bac / "fb.png", 0, 3)
            f_fin = part_changee(bac / "fa.png", bac / "fb.png", TOLERANCE_PAR_DEFAUT, 3)
            f_deb = part_changee(bac / "da.png", bac / "db.png", TOLERANCE_PAR_DEFAUT, 3)
            print(f"{nom:<56} fin brut {brut:>8} %   fin {f_fin:>8} %   début {f_deb:>8} %")
            if float(f_fin) > float(pire):
                pire = f_fin

            if fichier:
                # Le PIRE observe, et non la derniere valeur vue : un plancher ne redescend jamais.
                vu_f, vu_d = sol_fin.get(nom, ""), sol_deb.get(nom, "")
                sol_fin[nom] = f_fin if not vu_f or float(f_fin) > float(vu_f) else vu_f
                sol_deb[nom] = f_deb if not vu_d or float(f_deb) > float(vu_d) else vu_d
                nbp[nom] = str(int(nbp.get(nom, "0")) + 1)

    if fichier:
        entete = [
            f"# Plancher de bruit PAR CAS, à {TOLERANCE_PAR_DEFAUT} % de tolérance.",
            "# Colonnes : cas, plancher de la PREMIÈRE image, plancher de la DERNIÈRE, nombre de paires.",
            "# ⚠️ Le PIRE plancher observé est gardé : sous-estimer le bruit fabrique des faux positifs.",
            "# ⚠️ Un plancher tiré d'UNE seule paire ne prouve rien. Lire la quatrième colonne.",
        ]
        corps = sorted(
            f"{nom}\t{sol_deb.get(nom) or '0.000'}\t{sol_fin[nom]}\t{nbp[nom]}" for nom in sol_fin
        )
        pathlib.Path(fichier).write_text("\n".join(entete + corps) + "\n", encoding="utf-8")
        print(f"Planchers écrits dans « {fichier} » : {len(sol_fin)} cas.")

    print()
    print(f"Plancher le plus haut à {TOLERANCE_PAR_DEFAUT} % de tolérance : {pire} %.")
    print(
        "⚠️ Ce nombre ne fait PAS un seuil : le retenir pour tous aveuglerait les cas stables, qui sont"
    )
    print("la grande majorité. Un écart se lit contre le plancher de SON cas.")
    return 0


def _auto_test() -> int:
    """Vingt-trois assertions, dont les deux bouts et les planchers par cas.

    Le premier cas est le plus important : deux dossiers vides doivent etre une PANNE, et non un
    « rien n a change ». Sans cette distinction, une comparaison qui a echoue a recuperer ses clips se
    lirait comme un produit stable (ADR 2748).
    """
    import contextlib
    import io
    import os

    if shutil.which("ffmpeg") is None:
        print("ffmpeg requis pour l'auto-test.", file=sys.stderr)
        return 2
    if shutil.which("compare") is None:
        print("ImageMagick requis pour l'auto-test.", file=sys.stderr)
        return 2

    echecs = 0

    def verifie(libelle: str, attendu: str, obtenu: str) -> None:
        nonlocal echecs
        if attendu in obtenu:
            print(f"  ✔ {libelle}")
        else:
            print(f"  ✘ {libelle} : « {attendu} » attendu, obtenu :")
            for l in obtenu.splitlines():
                print(f"      {l}")
            echecs = 1

    def joue(action) -> tuple[str, int]:
        tampon = io.StringIO()
        with contextlib.redirect_stdout(tampon), contextlib.redirect_stderr(tampon):
            code = action()
        return tampon.getvalue(), code

    with tempfile.TemporaryDirectory(prefix="vc-tournages-") as tmp:
        bac = pathlib.Path(tmp)

        def clip(fichier: pathlib.Path, couleur: str, taille: str = "160x120") -> None:
            subprocess.run(
                [
                    "ffmpeg",
                    "-v",
                    "error",
                    "-y",
                    "-f",
                    "lavfi",
                    "-i",
                    f"color=c={couleur}:s={taille}:d=1:r=10",
                    "-c:v",
                    "libx264",
                    "-pix_fmt",
                    "yuv420p",
                    str(fichier),
                ],
                capture_output=True,
                check=False,
            )

        # 1. Deux dossiers vides : une panne, et elle se DIT.
        (bac / "vide-a").mkdir()
        (bac / "vide-b").mkdir()
        sortie, _ = joue(lambda: comparer(bac / "vide-a", bac / "vide-b", bac / "rien"))
        verifie("deux dossiers vides sont une panne", "Aucun clip à comparer", sortie)
        verifie(
            "et l'index refuse de dire « rien n'a changé »",
            "rien à comparer",
            (bac / "rien" / "index.md").read_text(encoding="utf-8"),
        )

        # 2. Deux clips identiques : aucun cas ne bouge, et ca se dit aussi.
        (bac / "a").mkdir()
        (bac / "b").mkdir()
        clip(bac / "a/pareil.mp4", "white")
        shutil.copy(bac / "a/pareil.mp4", bac / "b/pareil.mp4")
        sortie, _ = joue(lambda: comparer(bac / "a", bac / "b", bac / "identique"))
        verifie("deux clips identiques ne bougent pas", "0 qui bougent", sortie)
        verifie(
            "et l'index le dit",
            "Aucun cas ne bouge",
            (bac / "identique" / "index.md").read_text(encoding="utf-8"),
        )

        # 3. Un cas present seulement apres : apparu.
        clip(bac / "b/neuf.mp4", "blue")
        sortie, _ = joue(lambda: comparer(bac / "a", bac / "b", bac / "apparu"))
        verifie("un cas apparu est compté", "1 apparu(s)", sortie)
        verifie(
            "et l'index le nomme",
            "cas **apparu**",
            (bac / "apparu" / "index.md").read_text(encoding="utf-8"),
        )

        # 4. Un cas present seulement avant : disparu.
        (bac / "b/neuf.mp4").unlink()
        clip(bac / "a/perdu.mp4", "green")
        sortie, _ = joue(lambda: comparer(bac / "a", bac / "b", bac / "disparu"))
        verifie("un cas disparu est compté", "1 disparu(s)", sortie)

        # 5. Un clip vraiment different : chiffre, accole, et localise.
        (bac / "a/perdu.mp4").unlink()
        clip(bac / "b/pareil.mp4", "black")
        sortie, _ = joue(lambda: comparer(bac / "a", bac / "b", bac / "change"))
        verifie("un clip différent est vu", "1 qui bougent", sortie)
        verifie(
            "et sa part de pixels vaut 100",
            "100.000 %",
            (bac / "change" / "index.md").read_text(encoding="utf-8"),
        )
        if (bac / "change/pareil.avant-apres.png").is_file() and (
            bac / "change/pareil.ou.png"
        ).is_file():
            print("  ✔ le montage ET la carte des différences existent")
        else:
            print("  ✘ le montage ou la carte des différences manque")
            echecs = 1

        # 6. Une GRANDE image, celle qui a fait tomber la premiere version.
        #
        # `identify -format '%[fx:w*h]'` rend le produit en NOTATION SCIENTIFIQUE des qu il depasse
        # le million - « 1.152e+06 » - et le test d entier qui le suivait refusait alors la mesure.
        # Les sept cas d un vrai tournage rendaient « ? », et l index annoncait « aucun cas ne bouge ».
        # Des clips de 160 × 120 ne peuvent PAS voir ce defaut : leur produit s ecrit en entier.
        for f in list((bac / "a").glob("*.mp4")) + list((bac / "b").glob("*.mp4")):
            f.unlink()
        clip(bac / "a/grand.mp4", "white", "1280x900")
        clip(bac / "b/grand.mp4", "black", "1280x900")
        sortie, _ = joue(lambda: comparer(bac / "a", bac / "b", bac / "grand"))
        verifie("une grande toile se mesure quand même", "0 mesure(s) impossible(s)", sortie)
        verifie(
            "et son écart est chiffré, pas rendu « ? »",
            "100.000 %",
            (bac / "grand" / "index.md").read_text(encoding="utf-8"),
        )

        # 7. Un OUTIL ABSENT, et c est le cas qui compte le plus.
        #
        # Sans lui, la garde d outils ne serait elle-meme gardee par rien. Le defaut qu elle ferme
        # s est produit pour de vrai : `comparer-tournages.yml` n installait pas ImageMagick, et les
        # cinquante cas d un vrai tournage se rangeaient en « mesure impossible » sans qu une ligne
        # ne dise pourquoi, le job restant vert (run 32640637929).
        #
        # Le chemin est reconstruit avec tout SAUF `compare` : c est la seule facon d eprouver
        # l absence sans desinstaller quoi que ce soit sur la machine qui lance l auto-test.
        (bac / "bin").mkdir()
        for outil in ("ffmpeg", "ffprobe", "convert", "identify"):
            chemin = shutil.which(outil)
            if chemin:
                (bac / "bin" / outil).symlink_to(chemin)
        ancien_path = os.environ["PATH"]
        os.environ["PATH"] = str(bac / "bin")
        try:
            sortie, code = joue(lambda: 0 if exige_ses_outils() else 3)
        finally:
            os.environ["PATH"] = ancien_path
        if code == 0:
            echecs = 1
        verifie("un outil absent est une panne, pas une mesure", "Outils absents", sortie)
        verifie("et l'outil manquant est NOMMÉ", "compare", sortie)

        # 8. Le fichier de planchers : ecrit, puis ACCUMULE en gardant le PIRE.
        #
        # La deuxieme paire est volontairement BRUYANTE la ou la premiere etait muette. Deux paires
        # identiques ne prouveraient que le compteur ; il faut un ecart qui monte pour prouver que
        # c est bien le maximum qui est retenu, et non la derniere valeur vue.
        for nom in ("p1a", "p1b", "p2a", "p2b", "p3a", "p3b", "d1", "d2"):
            (bac / nom).mkdir()
        clip(bac / "p1a/stable.mp4", "white")
        shutil.copy(bac / "p1a/stable.mp4", bac / "p1b/stable.mp4")
        clip(bac / "p2a/stable.mp4", "white")
        clip(bac / "p2b/stable.mp4", "black")

        sols = bac / "sols.tsv"
        joue(lambda: plancher(bac / "p1a", bac / "p1b", str(sols)))
        lu = "\n".join(
            l for l in sols.read_text(encoding="utf-8").splitlines() if not l.startswith("#")
        )
        verifie(
            "le plancher d'une paire muette vaut zéro aux deux bouts", "stable\t0.000\t0.000\t1", lu
        )

        joue(lambda: plancher(bac / "p2a", bac / "p2b", str(sols)))
        lu = "\n".join(
            l for l in sols.read_text(encoding="utf-8").splitlines() if not l.startswith("#")
        )
        verifie("une seconde paire bruyante ÉCRASE par le haut", "stable\t100.000\t100.000\t2", lu)

        # 9. Un ecart se lit contre le plancher de son cas, et le compte le dit.
        #
        # Le plancher de « stable » vaut 100 % : meme un ecart de 100 % ne le depasse pas. C est le
        # cas qui distingue ce classement de l ancien, ou tout ecart non nul « bougeait ».
        sortie, _ = joue(
            lambda: comparer(bac / "p2a", bac / "p2b", bac / "avec-sols", 5, str(sols))
        )
        verifie(
            "un écart égal à son plancher ne le dépasse pas", "0 au-dessus de leur plancher", sortie
        )
        avec = (bac / "avec-sols" / "index.md").read_text(encoding="utf-8")
        verifie("et le rapport au bruit propre est affiché", "**×", avec)
        verifie("avec les deux bouts, début et fin", "début", avec)

        # 10. Un cas ABSENT du fichier de planchers se dit, au lieu d etre pris pour stable.
        clip(bac / "p3a/inconnu.mp4", "white")
        clip(bac / "p3b/inconnu.mp4", "blue")
        joue(lambda: comparer(bac / "p3a", bac / "p3b", bac / "sans-sol", 5, str(sols)))
        verifie(
            "un cas sans plancher connu est SIGNALÉ",
            "plancher inconnu",
            (bac / "sans-sol" / "index.md").read_text(encoding="utf-8"),
        )

        # 11. Un fichier de planchers ANNONCE mais absent : une erreur de chemin.
        sortie, code = joue(
            lambda: comparer(
                bac / "p3a", bac / "p3b", bac / "sol-absent", 5, str(bac / "pas-la.tsv")
            )
        )
        if code == 0:
            echecs = 1
        verifie(
            "un fichier de planchers introuvable est une erreur",
            "Fichier de planchers introuvable",
            sortie,
        )

        # 12. C est bien la DERNIERE image qui est comparee, et non la premiere.
        #
        # Depuis #4296 ce cas prouve les DEUX bouts d un seul coup : « virage » commence blanc et
        # finit noir, « noir » est noir de bout en bout. La fin doit donc valoir 0 % et le debut
        # 100 %. Un jour ou quelqu un inverserait les deux extractions, ce cas le dirait - ce qu une
        # simple assertion « 0 qui bougent » ne faisait pas (ADR 4274).
        clip(bac / "blanc.mp4", "white")
        clip(bac / "noir.mp4", "black")
        subprocess.run(
            [
                "ffmpeg",
                "-v",
                "error",
                "-y",
                "-i",
                str(bac / "blanc.mp4"),
                "-i",
                str(bac / "noir.mp4"),
                "-filter_complex",
                "[0:v][1:v]concat=n=2:v=1[v]",
                "-map",
                "[v]",
                "-c:v",
                "libx264",
                "-pix_fmt",
                "yuv420p",
                str(bac / "d1/virage.mp4"),
            ],
            capture_output=True,
            check=False,
        )
        shutil.copy(bac / "noir.mp4", bac / "d2/virage.mp4")
        joue(lambda: comparer(bac / "d1", bac / "d2", bac / "derniere"))
        derniere = (bac / "derniere" / "index.md").read_text(encoding="utf-8")
        verifie("la dernière image est bien la fin : 0 %", "fin 0.000 %", derniere)
        verifie("et la première est bien le début : 100 %", "début 100.000 %", derniere)

    if echecs == 0:
        print(
            "Auto-test de la comparaison de deux tournages : OK (23 cas, dont les deux bouts et les planchers par cas)."
        )
    else:
        print("Auto-test de la comparaison de deux tournages : ÉCHEC.")
    return echecs


if __name__ == "__main__":
    if "--auto-test" in sys.argv[1:2]:
        sys.exit(_auto_test())

    if sys.argv[1:2] == ["--plancher"]:
        if len(sys.argv) < 4:
            print(
                f"usage : {pathlib.Path(sys.argv[0]).name} --plancher <dossier A> <dossier B> [fichier]",
                file=sys.stderr,
            )
            sys.exit(2)
        if not exige_ses_outils():
            sys.exit(3)
        sys.exit(plancher(sys.argv[2], sys.argv[3], sys.argv[4] if len(sys.argv) > 4 else ""))

    if len(sys.argv) < 4:
        print(
            f"usage : {pathlib.Path(sys.argv[0]).name} <dossier avant> <dossier après> "
            "<dossier de sortie> [tolérance %] [planchers]",
            file=sys.stderr,
        )
        sys.exit(2)
    if not exige_ses_outils():
        sys.exit(3)
    sys.exit(
        comparer(
            sys.argv[1],
            sys.argv[2],
            sys.argv[3],
            sys.argv[4] if len(sys.argv) > 4 else TOLERANCE_PAR_DEFAUT,
            sys.argv[5] if len(sys.argv) > 5 else "",
        )
    )
