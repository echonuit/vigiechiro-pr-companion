#!/usr/bin/env bash
# Vérifie la typographie du CORPS d'une pull request.
#
# Le corps de la PR est ce qu'atteint quiconque remonte depuis `git log` : la compétence
# `clore-une-issue` en fait, avec le corps de l'issue, l'un des deux textes qui se relisent dans six
# mois sans le fil. C'est donc de la prose visible au sens de l'article A31.
#
# Cette prose échappait pourtant à toute règle opposable, pour une raison de mécanique : A31
# déclenchait « avant d'être commise », et un corps de PR n'est jamais commis. Rien dans le dépôt ne
# pouvait le refuser. Cf. ADR 4453.
#
# ## Ce qu'elle refuse, et sur quelle mesure
#
# Quatre défauts, chacun adossé à une décision déjà prise ailleurs. Les trois premiers ont été mesurés
# sur les dix-huit corps de PR du dépôt le 2026-08-25, le quatrième sur ceux du 2026-08-26 :
#
# | Défaut | Décision qui le porte | Mesure |
# |---|---|---|
# | tiret cadratin de prose | ADR 2843 | 2 lignes, 1 PR, deux vrais défauts |
# | apostrophe courbe | ADR 4368 | 0 |
# | élision sans apostrophe | le garde du TITRE de PR, même règle | 0 |
# | fermeture écrite en français | #4350, qui l'avait posée sans la rendre opposable | 2 en une session |
#
# Le quatrième diffère des trois autres : il ne refuse pas une typographie, il refuse une PROMESSE
# QUI NE SERA PAS TENUE. « Ferme #N » ne ferme rien, et surtout ne signale rien - la demande fusionne
# verte, l'issue reste ouverte, et on ne s'en aperçoit qu'en balayant les issues. #4350 avait écarté
# un garde parce qu'il aurait cherché ce qui MANQUE et rougi sur tout lot d'un EPIC ; celui-ci cherche
# ce qui est PRÉSENT, forme qui n'est jamais légitime, et laisse « Refs #N » tranquille.
#
# Zéro sur deux des trois règles : c'est un refus et non un cliquet, la zone étant à zéro le jour de
# la décision. Le cadratin, lui, a deux occurrences dans une PR déjà close, que rien ne peut plus
# retirer de la forge. C'est la démonstration du défaut : ce qui part ne se retire pas.
#
# ## Ce qu'elle ne voit PAS, et c'est assumé
#
# **Les quatre tics rhétoriques** de `CONTRIBUTING.md` - l'antithèse, l'annonce, le tricolon,
# l'aphorisme final. Aucun motif ne les distingue d'une phrase légitime, et l'ADR d'A31 le dit déjà
# pour le reste du dépôt : la grille sert à relire, les sept tics servent à refuser.
#
# **Un corps vide.** Qu'une PR doive porter un corps est une décision que personne n'a prise ici, et
# ce garde ne la prendra pas à sa place. Son auto-test l'épingle, pour qu'on le sache voulu.
#
# **Le corps d'une ISSUE.** Une issue n'a pas de contrôle qui puisse rougir : un atelier déclenché
# sur `issues` finirait rouge dans un onglet que personne n'ouvre. Ce corps-là reste tenu par la
# relecture et par la fonction de garde de `ouvrir-une-issue`.
#
# Usage : verifie-corps-pr.sh "<corps>"   (sortie 0 si conforme, 1 sinon)
set -euo pipefail


# ⟨locale⟩ Le verdict de ce garde ne doit pas dependre de l endroit d ou on l appelle. `grep -E` fait
# entrer `e` dans la plage `[a-z]` sous une locale francaise et l en sort sous `C` : le meme titre
# passait en local et echouait en CI (#4456). `C.UTF-8` fixe les deux : collation deterministe, et
# les octets multi-octets restent lisibles la ou le motif en a besoin.
export LC_ALL=C.UTF-8
CORPS="${1-}"

if [ "${CORPS}" = "--auto-test" ]; then
    # Les glyphes se CONSTRUISENT plutôt que de s'écrire, pour que ce fichier n'en porte aucun :
    # c'est l'idiome de `verifie-titre-pr.sh`, sans quoi le garde des cadratins compterait la prose
    # de son propre garde.
    CADRATIN=$(printf '\u2014')
    COURBE=$(printf '\u2019')
    TROISQUOTES='```'
    echecs=0
    cas=0
    rouges=0
    verifie() { # <attendu> <corps> <libellé>
        code=0
        cas=$((cas + 1))
        if [ "$1" != 0 ]; then rouges=$((rouges + 1)); fi
        "$0" "$2" >/dev/null 2>&1 || code=$?
        if [ "${code}" = "$1" ]; then
            echo "  ✔ $3"
        else
            echo "  ✘ $3 : attendu $1, obtenu ${code}"
            echecs=1
        fi
    }

    verifie 0 "Ce corps dit ce qui a ete fait, et pourquoi." "un corps conforme passe"
    verifie 1 "Le seuil tient ${CADRATIN} la mesure le dit." "un cadratin de prose est refusé"
    verifie 1 "L${COURBE}apostrophe courbe est refusée." "une apostrophe courbe est refusée"
    verifie 1 "Le garde tient, l ADR le dit." "une élision sans apostrophe est refusée"

    # #4546. La forge ne reconnait que `close`, `fix` et `resolve` : une fermeture ecrite en francais
    # ne ferme rien ET ne signale rien, ce qui la rend pire qu une absence de mot-cle. Le piege s est
    # produit deux fois dans une seule session, gabarit en place.
    verifie 1 "Ce que ce lot fait. Ferme #4502." "« Ferme #N » est refusé"
    # ACCENTUÉ, et c'est le cas qui éprouve le dépliage : le motif est écrit sans accent, donc un
    # `sans_accents` qui rendrait son entrée telle quelle laisserait passer la forme qu'on écrit
    # vraiment. Un témoin qui ne porterait que « Clot » ne prouverait rien de ce mécanisme.
    verifie 1 "Clôt #4502." "« Clôt #N » aussi, et il éprouve le dépliage des accents"
    verifie 1 "Résout le #4502." "et la forme avec article, accentuée elle aussi"
    verifie 1 "Corrige l issue #4502." "et celle qui nomme l issue"

    # Les contrôles NÉGATIFS, et ce sont eux qui rendent ce refus utilisable. Le garde ecarte par
    # #4350 cherchait ce qui MANQUE, un mot-cle de fermeture, et rougissait donc sur tout lot d un
    # EPIC. Celui-ci cherche ce qui est PRESENT : renvoyer sans clore reste vert.
    verifie 0 "Refs #4502" "« Refs #N » ne prétend rien et passe"
    verifie 0 "Rattache a #4502" "« Rattaché à #N » non plus"
    verifie 0 "Voir #4502 pour le detail" "un simple renvoi passe"
    verifie 0 "Le correctif de #4502 tient" "un renvoi au fil d une phrase passe"
    verifie 0 "#4502 a pose la question" "un renvoi en tete de phrase passe"
    # `close` est un mot-cle ANGLAIS valide : le refuser ferait recrire en francais ce qui marchait.
    verifie 0 "Close #4502" "le mot-clé anglais reste vert"
    verifie 0 "Fixes #4502" "et ses deux jumeaux"
    verifie 0 "Resolves #4502" "aussi"

    # Les exemptions. Un corps de PR colle des sorties de commande et cite des glyphes : les refuser
    # rendrait le garde contournable par suppression de la citation, ce qui est pire que muet.
    verifie 0 "Sortie collee :
${TROISQUOTES}
verdict ${CADRATIN} 0 regle, l ADR absente
${TROISQUOTES}" "un bloc de code cloture est épargné"
    verifie 0 "Le glyphe de valeur absente s'ecrit « ${CADRATIN} »." "le glyphe entre guillemets français est épargné"
    verifie 0 "Le motif \`[-${CADRATIN}]\` accepte les deux formes." "le glyphe en code en ligne est épargné"

    # Contrôles NÉGATIFS : la règle de l'élision reste étroite, comme dans le garde du titre.
    verifie 0 "Le point C3 et le carre A1 sont distincts." "un code de point ne déclenche pas"
    verifie 0 "Le n° 4 est traite." "un numéro ne déclenche pas"
    verifie 0 "Deux semeurs prennent l'entree legere." "une élision correcte ne déclenche pas"
    # #4483. Une lettre isolee employee comme SYMBOLE n est pas une elision. Les quatre formes
    # mesurees sur les 21 234 lignes de prose du depot, ou elles valaient 50 faux positifs sur 120.
    verifie 0 "Le decoupage se fait a 5 s reelles, mesure a 10,5 s." "un symbole d'unité après un nombre ne déclenche pas"
    verifie 0 "Un ecran qui compose N lignes paie N requetes." "une majuscule-symbole au fil d'une phrase ne déclenche pas"
    verifie 0 "    participant S as Service" "un alias de diagramme ne déclenche pas"
    verifie 0 "Le seuil est -S info, et non warning." "un drapeau de commande ne déclenche pas"
    # La contrepartie : en TETE de phrase la majuscule reste vue, sinon « L ADR » redeviendrait muet.
    verifie 1 "Le garde tient. L amendement le dit." "une élision majuscule en tête de phrase reste refusée"

    # #4786. Ce qui SUIT decide. Une sortie d outil citee commence par un symbole isole, et le
    # motif la lisait comme une elision - en tete de ligne, indentee ou non. L indentation n y
    # etait pour rien : c est la branche `^` qui s applique la.
    verifie 0 "M scripts/adr/truc.py" "une sortie d'outil en tête de ligne ne déclenche pas"
    verifie 0 "  M scripts/adr/truc.py" "la même, indentée, ne déclenche pas non plus"
    verifie 0 "Le seuil monte. N observation(s) ont ete vues." "un symbole suivi d'un compte entre parenthèses ne déclenche pas"
    # Et la contrepartie du meme rejet : ce qui suit reste un MOT quand il porte un trait d union
    # ou une apostrophe. Sans ces deux cas, le retrecissement perdrait de vraies elisions.
    verifie 1 "Le garde tient. L auto-test le prouve." "une élision suivie d'un mot composé reste refusée"
    verifie 1 "Le garde tient. D abord, la mesure." "une élision suivie d'une virgule reste refusée"

    # #4786, second volet. Une decoration Markdown ouverte entre la lettre et le mot rendait le
    # garde muet. Ce qui separe les deux cas est de savoir si la lettre isolee est DEDANS la
    # decoration - un symbole etiquete - ou DEHORS, le mot seul etant decore - une elision.
    verifie 1 "Le garde tient. L **amendement** le dit." "une élision devant un mot en gras est refusée"
    verifie 1 "Le garde tient. L « amendement » le dit." "une élision devant des guillemets est refusée"
    verifie 1 "un truc l **ADR** dit" "la même règle vaut pour les minuscules"
    verifie 0 "| **C** Conformité | à établir |" "un symbole DANS le gras ne déclenche pas"
    verifie 0 "- **N** saute à la prochaine observation." "une touche en gras ne déclenche pas"

    # Épingle une DÉCISION, pas un comportement : un corps vide PASSE. Qui voudra le refuser fera
    # d'abord rougir ce cas, et lira dans l'en-tête pourquoi ce garde ne tranche pas cette
    # question-là.
    verifie 0 "" "un corps vide passe, faute de décision qui l'interdise"

    echo
    if [ "${rouges}" -eq 1 ]; then verbe=DOIT; else verbe=DOIVENT; fi
    echo "${cas} cas, dont ${rouges} qui ${verbe} rougir."
    exit "${echecs}"
fi

# Le corps passe par l'ENVIRONNEMENT et non par un tube : l'entrée standard porte déjà le
# programme python, et un corps de PR est de la donnée non fiable, qu'on n'interpole pas.
CORPS_PR="${CORPS}" python3 - <<'FIN'
import os
import re
import sys
import unicodedata

# Construits, jamais écrits : voir l'auto-test ci-dessus.
CADRATIN = chr(0x2014)
COURBE = chr(0x2019)
OUVRANT, FERMANT = chr(0x00AB), chr(0x00BB)

corps = os.environ["CORPS_PR"]

# La prose seule. Un corps de PR colle des sorties de commande, des extraits de code et des motifs
# d'expression régulière : les lire comme de la prose ferait rougir le garde sur un corps juste.
CITE = re.compile(
    "|".join([
        "`[^`]*`",                                             # code en ligne
        re.escape(OUVRANT) + r"\s*" + re.escape(CADRATIN) + r"\s*" + re.escape(FERMANT),
    ])
)
# L elision sans apostrophe, MEME regle que le garde du titre, et meme retrecissement (#4483) :
# une lettre isolee precedee d un NOMBRE est un symbole d unite (« 143 s la »), et une MAJUSCULE
# isolee au fil d une phrase est un symbole (« les N fichiers »), jamais une elision. La majuscule
# n est donc vue qu apres une espace elle-meme precedee d un caractere non alphanumerique, soit la
# tete de phrase. Le detail de la mesure est dans `verifie-titre-pr.sh`, qui porte la regle mere.
BORNE = r"[^\w'" + COURBE + r"]"

# Ce qui SUIT decide, et non ce qui precede (issue #4786). Une elision est suivie d un MOT francais,
# qui peut porter trait d union et apostrophe. Un symbole est suivi de ce qu il etiquette : un
# chemin, un compte entre parentheses, un identifiant. `M scripts/adr/truc.py` etait refuse comme
# une elision, en tete de ligne, indente ou non - la branche `^` du motif s applique la, et le
# retrecissement de #4483 ne couvrait que le milieu de phrase.
MOT = r"[^\W\d_](?:[^\W\d_]|['" + COURBE + r"-])*"

# Une decoration Markdown peut s ouvrir entre la lettre et le mot, et le garde ne voyait alors plus
# rien : « L **amendement** le dit » passait. Ce qui separe les deux cas est de savoir si la lettre
# isolee est DEDANS la decoration ou DEHORS. `**C** Conformite` etiquette une colonne, `**N** saute
# a la suivante` nomme une touche : la lettre y est dedans, c est un symbole. Dans `L **amendement**`
# la lettre est dehors et c est le MOT qui est decore : c est une elision.
OUVRE = r"(?:\*\*|\*|__|_|«\s*|\[)?"

# La fin du mot admet la decoration FERMANTE, sans quoi « L **amendement** » se couperait sur l
# etoile et la detection retomberait.
FIN_MOT = r"(\s|[*_)\]»,.;:!?]|$)"

ELISION_MIN = re.compile(
    r"(^" + BORNE + r"?|[^\d]" + BORNE + r")([ldnscjmt]|qu) +" + OUVRE + MOT + FIN_MOT)
ELISION_MAJ = re.compile(
    r"(^" + BORNE + r"?|[^\w]\s)([LDNSCJMT]|Qu) +" + OUVRE + MOT + FIN_MOT)

# Un verbe de fermeture FRANCAIS accole a un renvoi (#4546). GitHub ne reconnait que `close`, `fix`
# et `resolve` et leurs flexions : « Ferme #N » promet une fermeture que la forge ne fait pas, et
# elle ne fait aucun bruit - la demande fusionne verte, l issue reste ouverte.
#
# Le garde de #4350 avait ete ecarte parce qu il aurait cherche ce qui MANQUE, un mot-cle de
# fermeture, et aurait rougi sur tout lot d un EPIC qui renvoie sans clore. Celui-ci cherche ce qui
# est PRESENT, et cette forme n est jamais legitime : qui l ecrit veut clore et ne clot pas.
# « Refs #N » et « Rattache a #N » ne la portent pas, donc le lot passe.


def sans_accents(texte):
    """Le texte deplie, accents retires : « Resout » et « Résout » sont le meme verbe.

    Ecrire les deux formes dans le motif aurait double chaque alternative, et la moitie aurait
    vieilli en silence le jour ou une flexion manque.
    """
    return "".join(c for c in unicodedata.normalize("NFD", texte) if not unicodedata.combining(c))


FERMETURE_FR = re.compile(
    # `close` est ABSENT de cette liste, et c est le point delicat : « Close #N » est un mot-cle
    # ANGLAIS valide, que la forge honore. Le refuser rendrait le garde faux dans le sens qui coute
    # le plus cher - il ferait recrire en francais ce qui marchait.
    r"\b(ferme|fermee?s?|clot|clos|resout|resoud|resolue?s?|corrige|corrigee?s?"
    r"|repare|reparee?s?|termine|terminee?s?|acheve|achevee?s?)"
    r"\s+(l[ae']\s*)?(issue\s+)?#\d{1,5}\b",
    re.I,
)

fautes = []
dans_un_bloc = False
for numero, ligne in enumerate(corps.splitlines(), 1):
    if ligne.lstrip().startswith("```"):
        dans_un_bloc = not dans_un_bloc
        continue
    if dans_un_bloc:
        continue
    prose = CITE.sub("", ligne)
    if CADRATIN in prose:
        fautes.append((numero, "tiret cadratin", ligne.strip()))
    if COURBE in prose:
        fautes.append((numero, "apostrophe courbe", ligne.strip()))
    if ELISION_MIN.search(prose) or ELISION_MAJ.search(prose):
        fautes.append((numero, "élision sans apostrophe", ligne.strip()))
    if FERMETURE_FR.search(sans_accents(prose)):
        fautes.append((numero, "fermeture en français", ligne.strip()))

if not fautes:
    print("Corps conforme.")
    sys.exit(0)

REMEDES = {
    "tiret cadratin": (
        "Écrivez un deux-points, une virgule ou un tiret simple. La zone de prose du dépôt est à\n"
        "  tolérance zéro, et ce corps est publié sur la forge : il ne se retire pas.\n"
        "  Cf. dev-docs/decisions/typographie-cliquet-plutot-que-nettoyage.md"
    ),
    "apostrophe courbe": (
        "Le dépôt n'écrit que l'apostrophe droite ('), et ce corps ne fait pas exception.\n"
        "  Cf. dev-docs/decisions/le-depot-n-ecrit-qu-une-apostrophe.md"
    ),
    "fermeture en français": (
        "Écrivez le mot-clé en anglais : « Closes #N », « Fixes #N » ou « Resolves #N ». La forge ne\n"
        "  reconnaît qu'eux, et une fermeture écrite en français ne ferme rien ni ne signale rien : la\n"
        "  demande fusionne verte et l'issue reste ouverte. Pour renvoyer SANS clore - un lot dans un\n"
        "  EPIC - écrivez « Refs #N » ou « Rattaché à #N », qui ne prétendent rien."
    ),
    "élision sans apostrophe": (
        "Rétablissez l'apostrophe : « l'ADR », « d'une nuit », « n'est pas », « qu'il ».\n"
        "  L'habitude d'amputer les apostrophes vient du quoting shell des messages de commit ;\n"
        "  elle survit à la disparition de sa cause, et ce corps-ci se lit sur la forge.\n"
        "  Si la ligne CITE la sortie d'un outil, ce n'est pas une élision : mettez-la dans un bloc\n"
        "  clôturé par trois accents graves, que ce garde épargne entièrement. Indenter ne suffit\n"
        "  pas, une indentation portant aussi bien de la prose."
    ),
}

print(f"::error::Le corps de la PR porte {len(fautes)} defaut(s) de typographie.")
print()
for numero, defaut, ligne in fautes:
    print(f"  ligne {numero} : {defaut}")
    print(f"    {ligne[:110]}")
print()
for defaut in dict.fromkeys(d for _, d, _ in fautes):
    print(f"  {defaut} : {REMEDES[defaut]}")
    print()
print("Ce corps est de la prose visible au sens de l'article A31 : il se relit dans six mois sans le")
print("fil, et il est publié dès qu'il part. La grille complète est dans la compétence humaniser ;")
print("ce garde ne tient que ce qu'un motif peut voir.")
sys.exit(1)
FIN
