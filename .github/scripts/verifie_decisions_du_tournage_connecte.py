#!/usr/bin/env python3
"""Onze decisions des ateliers de tournage tiennent dans le YAML (#5221, porte du bash).

Elles ne se tiennent pas par un test : elles vivent dans la forme de trois ateliers, et rien ne les
relisait. Chacune a un cout connu si elle lache. Les cinq premieres sont celles du tournage connecte,
qui a donne son nom au fichier ; les quatre suivantes sont celles de la mesure des planchers (#5885) ;
la dixieme est celle de la comparaison d une execution (#5930) ; la onzieme est celle du resume de
la mesure (#5979).

1. **`comparer-tournages.yml` REFUSE la source `clips-connectes`.** Comparer un tournage connecte a
   un autre mesure la plateforme au lieu du produit, et rend un chiffre qui a l air juste (#4306).
   Ce refus s eprouve en **executant** le bloc, pas en le lisant.
2. **`publier-connecte` depend de `filmer` et porte une fonction d etat.** Sans la fonction, GitHub
   enveloppe la condition en `success() && (...)` sur TOUT le graphe amont : la porte qu on croit
   avoir ecrite n est jamais evaluee, rien ne rougit, et le job est simplement saute.
3. **Le controle du jeton vient AVANT le pas qui filme**, et reste garde par `inputs.connecte`.
   Sonder apres avoir filme ne coute rien et ne sert a rien ; sonder sans la garde refuserait tout
   tournage hors ligne, qui n a pas de jeton et n en veut pas.
4. **Le job qui filme NOMME son artefact, tentative comprise, et toute reprise le lit de lui.** Deux
   tentatives d une meme execution versaient sous le meme nom, et une publication relancee reprenait
   l artefact de la tentative ECHOUEE : sur l execution 37229250872, l oracle disait « 8 / 8 » et la
   pre-version recevait 3 clips et un index de 5 cas, sans que rien ne rougisse (#5797).
5. **Le tournage precedent de la plateforme de test est garde AVANT d etre ecrase.** La comparaison
   veut deux tournages, et la pre-version n en porte qu un. Recopier apres le versement garderait
   le tournage courant sous les deux noms : la comparaison dirait « rien n a change » entre un
   tournage et lui-meme, et ce serait vert (#5854).
6. **`mesurer-les-planchers.yml` REFUSE des tournages qui ne sont pas du meme commit, ou qui n ont pas
   conclu.** Un plancher est le bruit entre deux tournages IDENTIQUES. Pris entre deux commits, il
   range un changement du produit parmi le bruit, et la comparaison ne voit plus jamais ce
   changement-la : c est un plancher trop haut, donc un defaut qui ne rougit nulle part. Ce refus
   s eprouve lui aussi en **executant** le bloc.
7. **Un temoin n est jamais pris dans la mesure.** Le plancher est le pire de ses propres paires : un
   temoin qui en fait partie reste dessous par construction, et le controle serait vert sans avoir
   rien controle.
8. **La mesure n ecrit rien sur le depot.** Le fichier sort en artefact et se committe par une
   demande. Un plancher qui monte rend la comparaison moins sensible : cela se relit, et un atelier
   qui pousserait sur la branche par defaut l oterait a la relecture.
9. **Une execution ne rend ses clips que si elle porte UN artefact de clips, et un seul.** Une
   execution relancee en porte un par tentative (#5797). Les reprendre tous rangerait deux tournages
   sous le meme numero : la mesure comparerait alors un melange, et rien ne le dirait. Ajoutee a la
   cloture de #5644, dont la passe 6 a trouve ce refus ecrit et jamais eprouve. Et un artefact
   EXPIRE se dit : par son nom quand la forge le liste encore, par le rappel du delai quand elle ne
   liste plus rien (#5979). Sans cela une execution reussie de plus de quatorze jours etait refusee
   sans raison lisible, ou echouait au telechargement.
10. **`comparer-tournages.yml` reprend une EXECUTION avec les refus de la mesure, moins un.** Une
   source purement numerique est un numero d execution : une branche se compare ainsi a `main` avant
   fusion (#5930). L ADR 5854 avait ecarte cette entree parce qu elle ouvre une seconde voie de
   reprise, a garder en parite avec la premiere. La parite se tient ICI : les refus de la sixieme et
   de la neuvieme decision sont exiges de la comparaison par les memes phrases, face au meme leurre.
   Un seul ne se reprend pas, celui du meme commit : il est juste pour un plancher, et il rendrait
   la comparaison inutile, dont l objet est de mettre deux commits cote a cote. Et un artefact
   EXPIRE se dit par son nom, au lieu d echouer au telechargement sans raison lisible : la mesure le
   dit aussi depuis #5979, par les memes phrases, et la parite ne souffre plus cette exception.
11. **Le resume de la mesure montre les planchers a relire, ou dit qu il n y en a aucun.** La mesure
   nomme un plancher que dix-huit paires n ont pas approche (#4309), et l atelier le recopie dans son
   resume, seul endroit ou on le lit sans ouvrir un artefact. Ce bloc avait ete joue une fois, sans
   signal a montrer : le premier signal reel aurait ete son premier essai. Il se LANCE donc ici face
   a deux journaux, et la ligne du signal vient de l OUTIL, pas d une copie : si l outil change sa
   phrase, le filtre de l atelier ne trouve plus rien et ce garde rougit.

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

# La ligne du signal se demande a l outil qui l ecrit (onzieme decision).
sys.path.insert(0, str(RACINE / ".github" / "assets"))
from compare_tournages import signal_a_relire

ETAT = re.compile(r"\b(always|success|failure|cancelled)\s*\(\s*\)")


def _charge(chemin: pathlib.Path):
    import yaml

    return yaml.safe_load(chemin.read_text(encoding="utf-8"))


def _lance_un_bloc(bloc: str, leurre: str, variables: dict[str, str]) -> str:
    """Lance le `run:` d un atelier face a un `gh` leurre, et rend tout ce qu il a ecrit.

    Trois decisions s eprouvent ainsi, et chacune ecrivait son bac, son leurre et son appel. Le
    leurre vient en TETE du chemin : le bloc ne doit jamais atteindre le vrai `gh`, ni le reseau. Le
    code de sortie n est pas rendu, a dessein : un leurre fait echouer tout ce qu il ne connait pas,
    donc seul le MESSAGE dit laquelle des raisons a arrete le bloc.
    """
    with tempfile.TemporaryDirectory(prefix="vc-bloc-") as tmp:
        bac = pathlib.Path(tmp)
        (bac / "bin").mkdir()
        faux = bac / "bin" / "gh"
        faux.write_text(leurre, encoding="utf-8")
        faux.chmod(0o755)
        script = bac / "bloc.sh"
        script.write_text(bloc, encoding="utf-8")
        env = dict(os.environ)
        env["PATH"] = f"{bac / 'bin'}{os.pathsep}{env.get('PATH', '')}"
        env.update(variables)
        rendu = subprocess.run(
            ["bash", str(script)], capture_output=True, text=True, cwd=bac, env=env, check=False
        )
        return rendu.stdout + rendu.stderr


# Un `gh` qui echoue toujours : le refus d une source doit tomber AVANT tout appel reseau, et si un
# jour il tombe apres, on veut le voir ici plutot qu en production.
LEURRE_MUET = "#!/usr/bin/env bash\nexit 1\n"


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

    def lance(avant: str, apres: str) -> str:
        return _lance_un_bloc(blocs[0], LEURRE_MUET, {"AVANT": avant, "APRES": apres})

    if "ne se compare pas" not in lance("clips-connectes", "v1.0.0"):
        print("❌ comparer-tournages.yml n a pas refusé la source « clips-connectes ».")
        print("   Ce refus EST un garde : sans lui, la comparaison mesure la plateforme au lieu")
        print("   du produit et rend un chiffre qui a l air juste (#4306).")
        return False
    # La plateforme de test, elle, n est PAS refusee (#5793, ADR 5641) : son etat de depart est
    # declare, et la condition de sa comparaison se mesure (#5797), elle ne se refuse pas.
    if "ne se compare pas" in lance("clips-plateforme-de-test", "v1.0.0"):
        print("❌ comparer-tournages.yml refuse la source « clips-plateforme-de-test ».")
        print("   L ADR 5641 leve ce refus pour la plateforme de test : il ne vaut que pour la")
        print("   plateforme nationale, dont l ecran suit des donnees vivantes.")
        return False
    # Ni son tournage precedent, qui est l autre cote de sa comparaison (#5854).
    if "ne se compare pas" in lance(PRECEDENTE, "clips-plateforme-de-test"):
        print(f"❌ comparer-tournages.yml refuse la source « {PRECEDENTE} ».")
        print("   C est le second cote de la comparaison des clips de la plateforme de test :")
        print("   sans lui, elle n a rien a comparer.")
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


PRECEDENTE = "clips-plateforme-de-test-precedent"

# Ce qui ECRIT sur la pre-version courante. Le nom est suivi d une espace ou d une fin de mot, pour
# qu une ecriture sur la precedente, dont le nom commence pareil, ne compte pas.
ECRIT_SUR_LA_COURANTE = re.compile(
    r"gh release (?:upload|edit|create|delete-asset) clips-plateforme-de-test(?![-\w])"
)


def precedent_garde_avant_l_ecrasement(flux: pathlib.Path) -> bool:
    """La cinquieme : le tournage precedent se recopie AVANT toute ecriture sur la pre-version.

    L ordre se lit sur les pas du job, par ce que leur `run:` fait et non par leur nom : un pas
    renomme resterait juge, et un pas deplace rougit.
    """
    f = _charge(flux / "tournage-recette.yml")
    pas = (f["jobs"].get("publier-plateforme-de-test") or {}).get("steps") or []
    blocs = [str(e.get("run", "")) for e in pas]
    recopies = [i for i, b in enumerate(blocs) if f"gh release upload {PRECEDENTE} " in b]
    ecritures = [i for i, b in enumerate(blocs) if ECRIT_SUR_LA_COURANTE.search(b)]
    if not recopies:
        print(f"❌ Aucun pas de `publier-plateforme-de-test` ne verse sur « {PRECEDENTE} ».")
        print(
            "   Le tournage precedent est ecrase sans etre garde : la comparaison n a qu un cote."
        )
        return False
    if not ecritures:
        print(
            "❌ Aucun pas de `publier-plateforme-de-test` n ecrit sur « clips-plateforme-de-test » :"
        )
        print("   ce garde ne sait plus par rapport a quoi juger l ordre, et il le dit.")
        return False
    if min(recopies) > min(ecritures):
        print(
            f"❌ La recopie du tournage precedent vient APRES une ecriture sur la pre-version "
            f"courante (pas {min(ecritures) + 1} puis {min(recopies) + 1})."
        )
        print("   Elle garderait le tournage courant sous les deux noms, et la comparaison dirait")
        print("   « rien n a change » entre un tournage et lui-meme.")
        return False
    if "gh release download clips-plateforme-de-test " not in blocs[min(recopies)]:
        print("❌ Le pas qui verse sur la precedente ne reprend pas les pieces de la courante :")
        print("   ce qu il garde ne vient pas du tournage precedent.")
        return False
    return True


SORTIE_ARTEFACT = "${{ needs.filmer.outputs.artefact }}"


def artefact_nomme_par_le_tournage(flux: pathlib.Path) -> bool:
    """La quatrieme : `filmer` nomme son artefact avec SA tentative, et toute reprise le lit de lui.

    Trois proprietes, parce que chacune seule laisse passer le defaut. Le nom porte la tentative,
    sinon deux tentatives se recouvrent. Le versement emploie ce nom, sinon la sortie ment. Et la
    reprise lit la SORTIE de `filmer` au lieu de recalculer le nom : une publication relancee seule
    porte un autre numero de tentative que le filmage qu elle doit reprendre.
    """
    f = _charge(flux / "tournage-recette.yml")
    filmer = f["jobs"].get("filmer") or {}
    nom = str((filmer.get("outputs") or {}).get("artefact", ""))
    tiennent = True
    if "github.run_attempt" not in nom:
        print("❌ `filmer` ne nomme plus son artefact avec sa tentative :")
        print(f"     outputs.artefact: {nom or '(absente)'}")
        print(
            "   Deux tentatives verseraient sous le même nom, et la reprise prendrait l'une ou l'autre."
        )
        tiennent = False
    verses = [
        str((pas.get("with") or {}).get("name", ""))
        for pas in filmer.get("steps") or []
        if str(pas.get("uses", "")).startswith("actions/upload-artifact@")
    ]
    if verses != [nom]:
        print("❌ `filmer` ne verse pas son artefact sous le nom qu'il annonce en sortie :")
        print(f"     annoncé : {nom or '(rien)'}")
        print(f"     versé   : {verses or '(aucun versement)'}")
        tiennent = False
    reprises = 0
    for cle, job in f["jobs"].items():
        for pas in job.get("steps") or []:
            if not str(pas.get("uses", "")).startswith("actions/download-artifact@"):
                continue
            reprises += 1
            repris = str((pas.get("with") or {}).get("name", ""))
            if repris != SORTIE_ARTEFACT:
                print(f"❌ `{cle}` reprend un artefact dont il recalcule le nom :")
                print(f"     name: {repris or '(absent)'}")
                print(f"   Attendu : {SORTIE_ARTEFACT}, que seul le job qui filme sait dire.")
                tiennent = False
    if reprises == 0:
        # Zero reprise trouvee serait verte pour la pire des raisons : le motif ne correspond plus.
        print(
            "❌ Aucun pas ne reprend d'artefact dans tournage-recette.yml : le relevé ne lit plus rien."
        )
        tiennent = False
    return tiennent


MESURE = "mesurer-les-planchers.yml"

# Le leurre des tournages, COMMUN a la mesure et a la comparaison. C est par lui que la parite des
# deux reprises se tient (#5930) : un refus exige de l une et de l autre l est face aux memes
# executions, et un leurre par atelier laisserait chacun repondre a sa propre fiction.
#
#   n   commit   conclusion   branche       artefacts de clips
#   1   a        success      main          un
#   2   a        success      main          deux (une execution relancee, #5797)
#   3   a        success      main          aucun
#   4   a        success      main          un, EXPIRE
#   5   b        success      une-branche   un
#   6   a        failure      main          un
#
# `gh run view <n> --json <champs> --jq ...` rend le commit et la conclusion, la branche entre les
# deux pour qui la demande : la mesure lit deux champs, la comparaison trois. `gh api
# .../runs/<n>/artifacts` rend les noms, suivis de l etat d expiration pour qui le demande. `gh run
# download` fabrique un clip, pour que le bon cas aille au bout au lieu d echouer plus loin pour
# une autre raison. Une seule version publiee existe, `v1.0.0`, et elle porte un clip. Tout autre
# appel echoue, pour qu un bloc qui irait chercher autre chose le dise au lieu de passer.
LEURRE_DES_TOURNAGES = """#!/usr/bin/env bash
if [ "$1 $2" = "run view" ]; then
  principale="" ; autre=""
  case "$5" in *headBranch*) principale=" main" ; autre=" une-branche" ;; esac
  case "$3" in
    1|2|3|4) echo "aaaaaaaaaaaaaaaa${principale} success" ;;
    5) echo "bbbbbbbbbbbbbbbb${autre} success" ;;
    6) echo "aaaaaaaaaaaaaaaa${principale} failure" ;;
    *) exit 1 ;;
  esac
  exit 0
fi
if [ "$1" = "api" ]; then
  vif="" ; perime=""
  case "$*" in *expired*) vif=" false" ; perime=" true" ;; esac
  case "$2" in
    */runs/1/artifacts|*/runs/5/artifacts|*/runs/6/artifacts) echo "clips-toutes-ubuntu-1${vif}" ;;
    */runs/2/artifacts) printf 'clips-toutes-ubuntu-1%s\\nclips-toutes-ubuntu-2%s\\n' "$vif" "$vif" ;;
    */runs/3/artifacts) ;;
    */runs/4/artifacts) echo "clips-toutes-ubuntu-1${perime}" ;;
    *) exit 1 ;;
  esac
  exit 0
fi
if [ "$1 $2" = "run download" ]; then
  mkdir -p "$7/target/recette/clips" && : > "$7/target/recette/clips/Cas.mp4"
  exit 0
fi
if [ "$1 $2 $3" = "release view v1.0.0" ]; then
  exit 0
fi
if [ "$1 $2 $3" = "release download v1.0.0" ]; then
  : > "$5/Cas.mp4"
  exit 0
fi
exit 1
"""


def _joue_le_choix_des_tournages(flux: pathlib.Path):
    """Le bloc qui choisit les tournages de la mesure, pret a etre lance, ou None s il a change de forme.

    Rend une fonction `(executions, temoins) -> sortie`. Le bloc se termine par le controle, donc il
    ne touche a rien d autre que le leurre : ni reseau, ni disque.
    """
    if not (flux / MESURE).is_file():
        print(f"❌ L atelier {MESURE} a disparu : la mesure des planchers n a plus de flux.")
        return None
    blocs = [
        e["run"]
        for j in (_charge(flux / MESURE)["jobs"]).values()
        for e in j.get("steps", [])
        if "du_meme_commit()" in e.get("run", "")
    ]
    if len(blocs) != 1:
        print(f"❌ {len(blocs)} pas définissent `du_meme_commit()` dans {MESURE}, attendu 1.")
        print("   La forme a changé : ce garde ne sait plus quoi lancer, et il le dit plutôt que")
        print("   de rendre un vert qui ne vaudrait rien.")
        return None

    def lance(executions: str, temoins: str = "") -> str:
        return _lance_un_bloc(
            blocs[0], LEURRE_DES_TOURNAGES, {"EXECUTIONS": executions, "TEMOINS": temoins}
        )

    return lance


AUTRE_COMMIT = "ne sont pas du même commit"
# Les phrases de refus que la mesure ET la comparaison doivent rendre. Les nommer une fois est ce
# qui fait de la parite une exigence : deux chaines recopiees pourraient diverger sans rien rougir.
PAS_CONCLU = "pas conclu en succès"


def planchers_d_un_seul_commit(flux: pathlib.Path) -> bool:
    """La sixieme : la mesure refuse deux commits, et un tournage qui n a pas conclu.

    Le verdict se prend sur le MESSAGE, comme pour la premiere : le leurre ne sait repondre qu a une
    question, et un code de sortie ne dirait pas laquelle des raisons a fait echouer le bloc.
    """
    lance = _joue_le_choix_des_tournages(flux)
    if lance is None:
        return False
    tiennent = True
    # L autre bord D ABORD : sans lui, un bloc qui refuserait tout serait vert sur les trois refus.
    if "Même commit pour 4 exécution(s)" not in lance("1 2", "3 4"):
        print(f"❌ {MESURE} n accepte pas quatre tournages du même commit, témoins compris.")
        print(
            "   Les refus qui suivent ne discriminent plus : ils tomberaient aussi sur le bon cas."
        )
        tiennent = False
    if AUTRE_COMMIT not in lance("1 5"):
        print(f"❌ {MESURE} mesure un plancher entre deux COMMITS.")
        print(
            "   Un changement du produit y passe pour du bruit, et la comparaison ne le verra plus."
        )
        tiennent = False
    if AUTRE_COMMIT not in lance("1 2", "3 5"):
        print(f"❌ {MESURE} accepte un témoin d un autre commit que la mesure.")
        print("   Son écart serait un changement du produit, lu comme un plancher trop bas.")
        tiennent = False
    if PAS_CONCLU not in lance("1 6"):
        print(f"❌ {MESURE} mesure un tournage qui n a pas conclu.")
        print("   Il lui manque des clips, et ceux qu il porte ont pu s arrêter avant leur fin.")
        tiennent = False
    return tiennent


def temoins_hors_de_la_mesure(flux: pathlib.Path) -> bool:
    """La septieme : un temoin pris dans la mesure est refuse."""
    lance = _joue_le_choix_des_tournages(flux)
    if lance is None:
        return False
    if "fait partie de la mesure" not in lance("1 2 3", "3 4"):
        print(f"❌ {MESURE} accepte un témoin qui fait partie de la mesure.")
        print("   Le plancher est le pire de ses propres paires : ce témoin reste dessous par")
        print("   construction, et le contrôle est vert sans avoir rien contrôlé.")
        return False
    return True


def mesure_sans_ecriture(flux: pathlib.Path) -> bool:
    """La huitieme : aucune permission d ecriture, ni sur l atelier ni sur un de ses jobs."""
    if not (flux / MESURE).is_file():
        print(f"❌ L atelier {MESURE} a disparu : la mesure des planchers n a plus de flux.")
        return False
    f = _charge(flux / MESURE)
    portees = {"l atelier": f.get("permissions")}
    for nom, job in f["jobs"].items():
        if "permissions" in job:
            portees[f"le job `{nom}`"] = job["permissions"]
    tiennent = True
    for ou, droits in portees.items():
        # Absentes, les permissions sont celles du depot, qui peuvent ecrire : le silence ne vaut
        # pas lecture seule. Et `write-all` est une chaine, pas une table.
        if not isinstance(droits, dict) or any(v != "read" for v in droits.values()):
            print(f"❌ {MESURE} peut écrire sur le dépôt, par {ou} : permissions = {droits!r}.")
            print("   Un plancher se committe par une demande, pour être relu avant de compter.")
            tiennent = False
    return tiennent


PLUSIEURS_ARTEFACTS = "ne porte pas UN artefact de clips"


def un_seul_artefact_de_clips(flux: pathlib.Path) -> bool:
    """La neuvieme : la reprise refuse une execution qui porte zero ou plusieurs artefacts de clips."""
    if not (flux / MESURE).is_file():
        print(f"❌ L atelier {MESURE} a disparu : la mesure des planchers n a plus de flux.")
        return False
    blocs = [
        e["run"]
        for j in (_charge(flux / MESURE)["jobs"]).values()
        for e in j.get("steps", [])
        if 'startswith("clips-")' in e.get("run", "")
    ]
    if len(blocs) != 1:
        print(f"❌ {len(blocs)} pas reprennent les artefacts de clips dans {MESURE}, attendu 1.")
        print("   La forme a changé : ce garde ne sait plus quoi lancer, et il le dit.")
        return False

    def lance(executions: str) -> str:
        return _lance_un_bloc(
            blocs[0],
            LEURRE_DES_TOURNAGES,
            {"EXECUTIONS": executions, "TEMOINS": "", "GH_REPO": "depot/essai"},
        )

    tiennent = True
    # L autre bord d abord : une execution a UN artefact va au bout, et compte son clip.
    if "Exécution 1 : 1 clip(s)." not in lance("1"):
        print(f"❌ {MESURE} ne reprend pas une exécution qui porte un seul artefact de clips.")
        print("   Les refus qui suivent ne discriminent plus.")
        tiennent = False
    if PLUSIEURS_ARTEFACTS not in lance("2"):
        print(f"❌ {MESURE} reprend une exécution qui porte DEUX artefacts de clips.")
        print("   Deux tentatives se rangeraient sous le même numéro, et la mesure les mêlerait.")
        tiennent = False
    if PLUSIEURS_ARTEFACTS not in lance("3"):
        print(f"❌ {MESURE} ne dit rien d une exécution sans artefact de clips.")
        tiennent = False
    if DELAI not in lance("3"):
        print(f"❌ {MESURE} ne rappelle pas le délai de garde d un artefact de clips.")
        print("   Une exécution réussie qui ne liste plus rien serait refusée sans raison lisible.")
        tiennent = False
    if EXPIRE not in lance("4"):
        print(f"❌ {MESURE} ne dit pas qu un artefact de clips a expiré.")
        print(
            "   Le téléchargement échouerait sans raison lisible, quatorze jours après le tournage."
        )
        tiennent = False
    return tiennent


COMPARAISON = "comparer-tournages.yml"
EXPIRE = "a expiré"
DELAI = "gardé quatorze jours : passé ce délai"


def une_execution_se_compare(flux: pathlib.Path) -> bool:
    """La dixieme : la comparaison reprend une execution, avec les refus de la mesure moins un.

    Les phrases exigees sont celles de la sixieme et de la neuvieme, face au meme leurre : c est
    ainsi que les deux reprises restent en parite, ce que l ADR 5854 demandait de toute seconde
    voie. Le verdict se prend sur le MESSAGE, pour la raison de la premiere.
    """
    blocs = [
        e["run"]
        for j in (_charge(flux / COMPARAISON)["jobs"]).values()
        for e in j.get("steps", [])
        if "reprendre()" in e.get("run", "")
    ]
    if len(blocs) != 1:
        # La premiere decision le dit deja, avec sa raison : ici on se borne a ne rien lancer.
        return False

    def lance(avant: str, apres: str) -> str:
        return _lance_un_bloc(
            blocs[0],
            LEURRE_DES_TOURNAGES,
            {"AVANT": avant, "APRES": apres, "BANC": "bash", "GH_REPO": "depot/essai"},
        )

    tiennent = True
    # L autre bord D ABORD, et c est aussi le seul refus de la mesure qui ne se reprend PAS : deux
    # executions de deux COMMITS vont au bout, chacune disant son commit et sa branche.
    rendu = lance("1", "5")
    if (
        "Exécution 1 (aaaaaaaaa, branche main) : 1 clip(s)." not in rendu
        or "Exécution 5 (bbbbbbbbb, branche une-branche) : 1 clip(s)." not in rendu
    ):
        print(f"❌ {COMPARAISON} ne compare pas deux exécutions de deux commits.")
        print("   C est l objet de cette source : une branche contre `main`, avant fusion. Et les")
        print("   refus qui suivent ne discriminent plus, s ils tombent aussi sur le bon cas.")
        tiennent = False
    # La paire mixte : une version publiee d un cote, une execution de l autre.
    rendu = lance("v1.0.0", "1")
    if "« v1.0.0 » : 1 clip(s)." not in rendu or "Exécution 1 (aaaaaaaaa" not in rendu:
        print(f"❌ {COMPARAISON} ne compare plus une version publiée à une exécution.")
        tiennent = False
    # Un numero n est JAMAIS cherche parmi les versions : inconnu, il se dit illisible.
    rendu = lance("1", "99")
    if "ne se lit pas" not in rendu or "ni un tag" in rendu:
        print(f"❌ {COMPARAISON} cherche un numéro d exécution parmi les versions publiées.")
        print("   Une exécution inconnue serait annoncée comme un tag introuvable.")
        tiennent = False
    if PAS_CONCLU not in lance("1", "6"):
        print(f"❌ {COMPARAISON} compare un tournage qui n a pas conclu.")
        print(f"   {MESURE} le refuse : les deux reprises ne sont plus en parité.")
        tiennent = False
    if PLUSIEURS_ARTEFACTS not in lance("1", "2"):
        print(f"❌ {COMPARAISON} reprend une exécution qui porte DEUX artefacts de clips.")
        print(f"   {MESURE} le refuse : les deux reprises ne sont plus en parité.")
        tiennent = False
    if PLUSIEURS_ARTEFACTS not in lance("1", "3"):
        print(f"❌ {COMPARAISON} ne dit rien d une exécution sans artefact de clips.")
        print(f"   {MESURE} le refuse : les deux reprises ne sont plus en parité.")
        tiennent = False
    if DELAI not in lance("1", "3"):
        print(f"❌ {COMPARAISON} ne rappelle pas le délai de garde d un artefact de clips.")
        print(f"   {MESURE} le rappelle : les deux reprises ne sont plus en parité.")
        tiennent = False
    if EXPIRE not in lance("1", "4"):
        print(f"❌ {COMPARAISON} ne dit pas qu un artefact de clips a expiré.")
        print(
            "   Le téléchargement échouerait sans raison lisible, quatorze jours après le tournage."
        )
        tiennent = False
    # Lire les artefacts d une AUTRE execution demande `actions: read`. Absente, la reprise echoue
    # sur la forge et nulle part ailleurs : le leurre, lui, ne connait pas les permissions.
    droits = _charge(flux / COMPARAISON).get("permissions")
    if not isinstance(droits, dict) or droits.get("actions") != "read":
        print(f"❌ {COMPARAISON} ne déclare pas `actions: read` : permissions = {droits!r}.")
        print("   Sans elle, l atelier ne peut pas lire les artefacts d une autre exécution.")
        tiennent = False
    return tiennent


RESUME = "### Planchers mesurés"
AUCUN_SIGNAL = "Aucun plancher à relire"


def le_resume_montre_les_planchers_a_relire(flux: pathlib.Path) -> bool:
    """La onzieme : le resume de la mesure recopie les signaux du journal, ou dit qu il n y en a pas."""
    if not (flux / MESURE).is_file():
        # La neuvieme le dit deja, avec sa raison.
        return False
    blocs = [
        e["run"]
        for j in (_charge(flux / MESURE)["jobs"]).values()
        for e in j.get("steps", [])
        if RESUME in e.get("run", "")
    ]
    if len(blocs) != 1:
        print(f"❌ {len(blocs)} pas écrivent le résumé de la mesure dans {MESURE}, attendu 1.")
        print("   La forme a changé : ce garde ne sait plus quoi lancer, et il le dit.")
        return False
    if "compare_tournages.py" in blocs[0]:
        print(f"❌ Le pas qui écrit le résumé de {MESURE} lance aussi la mesure.")
        print("   Il ne se lance pas seul face à un journal : son résumé n est éprouvé par rien.")
        return False

    signal = signal_a_relire("Classe.cas", "0.807", 18)

    def lance(journal: list[str]) -> str:
        pose = "cat > mesure.log <<'JOURNAL'\n" + "\n".join(journal) + "\nJOURNAL\n"
        return _lance_un_bloc(
            pose + blocs[0],
            LEURRE_MUET,
            {"EXECUTIONS": "1 2", "GITHUB_STEP_SUMMARY": "/dev/stdout"},
        )

    ecrits = "Planchers écrits dans « f.tsv » : 2 cas, pris par « un instrument »."
    tiennent = True
    rendu = lance([ecrits, signal])
    if f"- {signal}" not in rendu or AUCUN_SIGNAL in rendu:
        print(f"❌ Le résumé de {MESURE} ne montre pas un plancher que la mesure dit de relire.")
        print("   Le signal resterait dans un artefact que personne n ouvre.")
        tiennent = False
    rendu = lance([ecrits])
    if AUCUN_SIGNAL not in rendu or "- " in rendu:
        print(f"❌ Le résumé de {MESURE} ne dit pas qu aucun plancher n est à relire.")
        print("   Un résumé muet se lirait « vu », et c est l inverse qu il faut pouvoir lire.")
        tiennent = False
    return tiennent


def verdict(flux: pathlib.Path) -> bool:
    """Les onze, et le verdict d ensemble. Chacune s exprime, meme si une precedente a lache."""
    tiennent = [
        refus_de_la_source_connectee(flux),
        versement_conditionne(flux),
        controle_avant_le_tournage(flux),
        artefact_nomme_par_le_tournage(flux),
        precedent_garde_avant_l_ecrasement(flux),
        planchers_d_un_seul_commit(flux),
        temoins_hors_de_la_mesure(flux),
        mesure_sans_ecriture(flux),
        un_seul_artefact_de_clips(flux),
        une_execution_se_compare(flux),
        le_resume_montre_les_planchers_a_relire(flux),
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
def _recalcule_le_nom_a_la_reprise(dossier: pathlib.Path) -> None:
    """Le defaut d origine : une reprise qui recalcule le nom au lieu de le lire de `filmer`."""
    p = dossier / "tournage-recette.yml"
    t = p.read_text(encoding="utf-8")
    p.write_text(
        t.replace(
            "          name: ${{ needs.filmer.outputs.artefact }}",
            "          name: clips-${{ inputs.session }}-${{ inputs.plateforme }}",
            1,
        ),
        encoding="utf-8",
    )


def _retire_la_tentative_du_nom(dossier: pathlib.Path) -> None:
    """Le nom ne distingue plus deux tentatives, au versement comme en sortie."""
    p = dossier / "tournage-recette.yml"
    t = p.read_text(encoding="utf-8")
    p.write_text(t.replace("-${{ github.run_attempt }}", ""), encoding="utf-8")


def _verse_sous_un_autre_nom(dossier: pathlib.Path) -> None:
    """La sortie annonce un nom, et le versement en emploie un autre."""
    p = dossier / "tournage-recette.yml"
    t = p.read_text(encoding="utf-8")
    p.write_text(
        t.replace(
            "          name: clips-${{ inputs.session }}-${{ inputs.plateforme }}-${{ github.run_attempt }}\n          path:",
            "          name: clips-${{ inputs.session }}-${{ inputs.plateforme }}\n          path:",
            1,
        ),
        encoding="utf-8",
    )


def _recopie_apres_le_versement(dossier: pathlib.Path) -> None:
    """Le pas qui garde le precedent passe en DERNIER : il garderait le tournage courant."""
    import yaml

    p = dossier / "tournage-recette.yml"
    f = yaml.safe_load(p.read_text(encoding="utf-8"))
    pas = f["jobs"]["publier-plateforme-de-test"]["steps"]
    i = next(
        k for k, e in enumerate(pas) if f"gh release upload {PRECEDENTE} " in str(e.get("run", ""))
    )
    pas.append(pas.pop(i))
    p.write_text(yaml.safe_dump(f, allow_unicode=True, sort_keys=False), encoding="utf-8")


def _retire_la_recopie(dossier: pathlib.Path) -> None:
    """Plus aucun pas ne garde le tournage precedent."""
    import yaml

    p = dossier / "tournage-recette.yml"
    f = yaml.safe_load(p.read_text(encoding="utf-8"))
    pas = f["jobs"]["publier-plateforme-de-test"]["steps"]
    f["jobs"]["publier-plateforme-de-test"]["steps"] = [
        e for e in pas if f"gh release upload {PRECEDENTE} " not in str(e.get("run", ""))
    ]
    p.write_text(yaml.safe_dump(f, allow_unicode=True, sort_keys=False), encoding="utf-8")


def _refuse_la_precedente(dossier: pathlib.Path) -> None:
    """Le refus de `clips-connectes` s etend au tournage precedent de la plateforme de test."""
    p = dossier / "comparer-tournages.yml"
    t = p.read_text(encoding="utf-8")
    p.write_text(
        t.replace(
            '            if [ "$source" = "clips-connectes" ]; then',
            '            if [ "$source" = "clips-connectes" ] || [ "$source" = "'
            + PRECEDENTE
            + '" ]; then',
            1,
        ),
        encoding="utf-8",
    )


def _remplace_dans(dossier: pathlib.Path, atelier: str, avant: str, apres: str) -> None:
    """Une substitution dans un atelier, qui LEVE si son motif n y est plus.

    Une cassure qui ne casse rien laisserait l atelier sain, donc le cas rouge serait VERT et
    l auto-test le dirait. Mais il le dirait sans dire pourquoi : autant le dire ici.
    """
    p = dossier / atelier
    t = p.read_text(encoding="utf-8")
    if avant not in t:
        raise AssertionError(f"motif absent de {atelier} : {avant!r}")
    p.write_text(t.replace(avant, apres, 1), encoding="utf-8")


def _remplace_dans_la_mesure(dossier: pathlib.Path, avant: str, apres: str) -> None:
    _remplace_dans(dossier, MESURE, avant, apres)


def _mesure_entre_deux_commits(dossier: pathlib.Path) -> None:
    _remplace_dans_la_mesure(dossier, 'elif [ "$sha" != "$reference" ]; then', "elif false; then")


def _temoins_d_un_commit_libre(dossier: pathlib.Path) -> None:
    """Les temoins ne passent plus par le controle : seule la mesure est tenue au meme commit."""
    _remplace_dans_la_mesure(
        dossier,
        'du_meme_commit "${mesurees[@]}" "${hors_mesure[@]}"',
        'du_meme_commit "${mesurees[@]}"',
    )


def _mesure_un_tournage_echoue(dossier: pathlib.Path) -> None:
    _remplace_dans_la_mesure(dossier, 'if [ "$fin" != "success" ]; then', "if false; then")


def _temoin_pris_dans_la_mesure(dossier: pathlib.Path) -> None:
    _remplace_dans_la_mesure(dossier, 'if [ "$t" = "$m" ]; then', "if false; then")


def _la_mesure_peut_ecrire(dossier: pathlib.Path) -> None:
    _remplace_dans_la_mesure(
        dossier,
        "permissions:\n  contents: read\n  actions: read",
        "permissions:\n  contents: write\n  actions: read",
    )


def _la_mesure_tait_ses_permissions(dossier: pathlib.Path) -> None:
    """Sans le bloc, l atelier herite des permissions du depot : le silence n est pas la lecture."""
    _remplace_dans_la_mesure(dossier, "permissions:\n  contents: read\n  actions: read\n", "")


def _reprend_plusieurs_artefacts(dossier: pathlib.Path) -> None:
    """Le compte des artefacts n est plus controle : seul le vide est refuse."""
    _remplace_dans_la_mesure(
        dossier,
        """if [ -z "$liste" ] || [ "$(printf '%s\\n' "$liste" | wc -l)" -ne 1 ]; then""",
        'if [ -z "$liste" ]; then',
    )


def _reprend_sans_artefact(dossier: pathlib.Path) -> None:
    """Le vide n est plus refuse : seul le pluriel l est."""
    _remplace_dans_la_mesure(
        dossier,
        """if [ -z "$liste" ] || [ "$(printf '%s\\n' "$liste" | wc -l)" -ne 1 ]; then""",
        """if [ -n "$liste" ] && [ "$(printf '%s\\n' "$liste" | wc -l)" -ne 1 ]; then""",
    )


COMPTE_DES_ARTEFACTS = (
    """if [ -z "$liste" ] || [ "$(printf '%s\\n' "$liste" | wc -l)" -ne 1 ]; then"""
)


def _la_comparaison_ignore_les_executions(dossier: pathlib.Path) -> None:
    """Un numero redevient une source comme une autre, cherchee parmi les versions."""
    _remplace_dans(dossier, COMPARAISON, 'if [[ "$source" =~ ^[0-9]+$ ]]; then', "if false; then")


def _la_comparaison_exige_le_meme_commit(dossier: pathlib.Path) -> None:
    """Le refus de la mesure qui ne se reprend PAS, repris quand meme : tout autre commit est refuse."""
    _remplace_dans(
        dossier,
        COMPARAISON,
        'if [ "$fin" != "success" ]; then',
        'if [ "$fin" != "success" ] || [ "$sha" != "aaaaaaaaaaaaaaaa" ]; then',
    )


def _la_comparaison_prend_un_tournage_echoue(dossier: pathlib.Path) -> None:
    _remplace_dans(dossier, COMPARAISON, 'if [ "$fin" != "success" ]; then', "if false; then")


def _la_comparaison_reprend_plusieurs_artefacts(dossier: pathlib.Path) -> None:
    _remplace_dans(dossier, COMPARAISON, COMPTE_DES_ARTEFACTS, 'if [ -z "$liste" ]; then')


def _la_comparaison_reprend_sans_artefact(dossier: pathlib.Path) -> None:
    _remplace_dans(
        dossier,
        COMPARAISON,
        COMPTE_DES_ARTEFACTS,
        """if [ -n "$liste" ] && [ "$(printf '%s\\n' "$liste" | wc -l)" -ne 1 ]; then""",
    )


def _la_comparaison_reprend_un_artefact_expire(dossier: pathlib.Path) -> None:
    _remplace_dans(dossier, COMPARAISON, 'if [ "$perime" = "true" ]; then', "if false; then")


def _la_comparaison_ne_lit_plus_les_artefacts(dossier: pathlib.Path) -> None:
    """La permission qui ne se voit manquer que sur la forge."""
    _remplace_dans(
        dossier,
        COMPARAISON,
        "permissions:\n  contents: read\n  actions: read\n",
        "permissions:\n  contents: read\n",
    )


LIGNE_DU_DELAI = (
    """              echo "::error::Un artefact de clips est gardé quatorze jours : passé ce délai,"""
    """ l'exécution n'en liste plus."\n"""
)


def _la_mesure_reprend_un_artefact_expire(dossier: pathlib.Path) -> None:
    _remplace_dans_la_mesure(dossier, 'if [ "$perime" = "true" ]; then', "if false; then")


def _la_mesure_tait_le_delai(dossier: pathlib.Path) -> None:
    _remplace_dans_la_mesure(dossier, LIGNE_DU_DELAI, "")


def _la_comparaison_tait_le_delai(dossier: pathlib.Path) -> None:
    _remplace_dans(dossier, COMPARAISON, LIGNE_DU_DELAI, "")


def _le_resume_ne_recopie_plus_les_signaux(dossier: pathlib.Path) -> None:
    _remplace_dans_la_mesure(dossier, "grep '^Plancher à relire' mesure.log | sed 's/^/- /'", ":")


def _le_resume_tait_l_absence_de_signal(dossier: pathlib.Path) -> None:
    _remplace_dans_la_mesure(dossier, 'echo "Aucun plancher à relire à cette mesure."', ":")


CASSURES = (
    (_casse_le_refus, "le refus de clips-connectes neutralisé"),
    (_casse_la_fonction_d_etat, "publier-connecte privé de sa fonction d état"),
    (_deplace_la_sonde, "le contrôle du jeton déplacé après le tournage"),
    (_refuse_aussi_la_plateforme_de_test, "refus étendu à clips-plateforme-de-test"),
    (_casse_la_fonction_d_etat_de_test, "publier-plateforme-de-test privé de sa fonction d état"),
    (_recalcule_le_nom_a_la_reprise, "une reprise recalcule le nom de l artefact"),
    (_retire_la_tentative_du_nom, "nom d artefact sans numéro de tentative"),
    (_verse_sous_un_autre_nom, "versement sous un autre nom que la sortie"),
    (_recopie_apres_le_versement, "tournage précédent recopié après le versement"),
    (_retire_la_recopie, "aucun pas ne garde le tournage précédent"),
    (_refuse_la_precedente, "comparaison qui refuse le tournage précédent"),
    (_mesure_entre_deux_commits, "planchers mesurés entre deux commits"),
    (_temoins_d_un_commit_libre, "témoins libres de venir d un autre commit"),
    (_mesure_un_tournage_echoue, "mesure sur un tournage qui n a pas conclu"),
    (_temoin_pris_dans_la_mesure, "témoin pris dans la mesure"),
    (_la_mesure_peut_ecrire, "atelier de mesure autorisé à écrire"),
    (_la_mesure_tait_ses_permissions, "atelier de mesure sans permissions déclarées"),
    (_reprend_plusieurs_artefacts, "reprise d une exécution à deux artefacts de clips"),
    (_reprend_sans_artefact, "reprise d une exécution sans artefact de clips"),
    (_la_comparaison_ignore_les_executions, "numéro d exécution cherché parmi les versions"),
    (_la_comparaison_exige_le_meme_commit, "comparaison tenue à un seul commit"),
    (_la_comparaison_prend_un_tournage_echoue, "comparaison d un tournage qui n a pas conclu"),
    (_la_comparaison_reprend_plusieurs_artefacts, "comparaison d une exécution à deux artefacts"),
    (_la_comparaison_reprend_sans_artefact, "comparaison d une exécution sans artefact"),
    (_la_comparaison_reprend_un_artefact_expire, "comparaison d un artefact de clips expiré"),
    (_la_comparaison_ne_lit_plus_les_artefacts, "comparaison privée de sa lecture des artefacts"),
    (_la_mesure_reprend_un_artefact_expire, "mesure d un artefact de clips expiré"),
    (_la_mesure_tait_le_delai, "mesure qui tait le délai de garde des artefacts"),
    (_la_comparaison_tait_le_delai, "comparaison qui tait le délai de garde des artefacts"),
    (_le_resume_ne_recopie_plus_les_signaux, "résumé qui ne recopie plus les planchers à relire"),
    (_le_resume_tait_l_absence_de_signal, "résumé muet quand aucun plancher n est à relire"),
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
        for nom in ("tournage-recette.yml", "comparer-tournages.yml", MESURE):
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
            "✓ Les onze décisions des ateliers de tournage tiennent : refus de clips-connectes,"
            " versement"
        )
        print("  conditionné, contrôle du jeton avant le tournage, artefact nommé par le tournage,")
        print("  tournage précédent gardé avant l'écrasement, planchers d'un seul commit, témoins")
        print("  hors de la mesure, mesure sans écriture, un seul artefact de clips par exécution,")
        print("  une exécution comparée avec les refus de la mesure, un résumé de la mesure qui")
        print("  montre les planchers à relire.")
        sys.exit(0)
    print(
        "::error::Une décision des ateliers de tournage n est plus tenue par le YAML, cf. ci-dessus."
    )
    sys.exit(1)
