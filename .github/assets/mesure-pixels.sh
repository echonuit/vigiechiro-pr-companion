# Mesure la part de pixels qui diffèrent entre deux images. Destiné à être SOURCÉ, pas exécuté.
#
# ## Pourquoi ce fichier existe
#
# `compare-apercus.sh` et `compare-tournages.sh` portaient la même mesure, recopiée. Le 2026-08-23, le
# MÊME défaut a dû être corrigé dans les deux : au-delà du million, ImageMagick écrit ses comptes en
# NOTATION SCIENTIFIQUE - « 1.2034e+06 » - et les deux scripts s'arrêtaient au point.
#
# Le second n'a été trouvé que parce que le premier venait de l'être. Rien ne garantissait qu'on
# regarde le voisin ; une copie n'est juste que le jour où on l'écrit.
#
# ## Ce qu'elle rend
#
# Un pourcentage, ou « ? » si la mesure échoue. « ? » n'est PAS zéro : une mesure impossible et une
# absence de différence se réparent à des endroits différents, donc elles ne doivent pas se lire pareil
# (ADR 2748). C'est à l'appelant de compter les « ? » à part.
#
# Usage : part_changee <avant> <après> [tolérance %] [décimales]

### La part de pixels qui diffèrent, en pourcentage, ou "?" si la mesure échoue.
part_changee() {
  local avant="$1" apres="$2" tolerance="${3:-0}" decimales="${4:-2}" pixels total

  # `compare -metric AE` écrit son compte sur la SORTIE D'ERREUR et rend 1 dès qu'il y a une
  # différence : sans `|| true`, une mesure réussie passerait pour un échec.
  pixels=$(compare -metric AE -fuzz "${tolerance}%" "$avant" "$apres" null: 2>&1 || true)

  # Le PREMIER MOT, lu par `awk` - pas un découpage sur les chiffres. Au-delà d'un million de pixels
  # différents, `compare` rend « 1.2034e+06 (1) », et `${pixels%%[^0-9]*}` s'arrêtait au point pour
  # rendre « 1 », soit 0,00 % là où TOUT avait changé. Ce défaut ment dans le sens rassurant.
  pixels=${pixels%% *}
  [ -n "$pixels" ] || { printf '?'; return; }

  # `%w %h` et non `%[fx:w*h]`, même piège à l'autre bout du calcul : sur une toile de 1280 × 900 le
  # produit s'écrit « 1.152e+06 », que le test d'entier refusait. La mesure rendait alors « ? » sur
  # TOUTES les grandes images - 33 des captures du dépôt dépassent ce seuil.
  total=$(identify -format '%w %h' "$apres" 2>/dev/null) || { printf '?'; return; }

  LC_ALL=C awk -v p="$pixels" -v wh="$total" -v n="$decimales" 'BEGIN {
    split(wh, d, " ")
    t = d[1] * d[2]
    # Une valeur illisible se dit, elle ne se prend pas pour un zéro.
    if (t <= 0 || p "" !~ /^[0-9]/) { printf "?"; exit }
    printf "%." n "f", 100 * (p + 0) / t
  }'
}
