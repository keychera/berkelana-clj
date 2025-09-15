(ns assets.chars
  (:require [play-cljc.transforms :as t]
            [play-cljc.math :as m]
            [play-cljc.instances :as i]))

(defn get-baked-char [font ch]
  (let [{:keys [baked-chars first-char]} (:baked-font font)
        char-code (- #?(:clj (int ch) :cljs (.charCodeAt ch 0)) first-char)
        baked-char (nth baked-chars char-code)]
    baked-char))

(defn crop-char [{:keys [baked-font] :as font-entity} ch]
  (let [{:keys [baked-chars baseline first-char]} baked-font
        char-code (- #?(:clj (int ch) :cljs (.charCodeAt ch 0)) first-char)
        baked-char (nth baked-chars char-code)
        {:keys [x y w h xoff yoff]} baked-char]
    (-> font-entity
        (t/crop x y w h)
        (assoc-in [:uniforms 'u_scale_matrix]
                  (m/scaling-matrix w h))
        (assoc-in [:uniforms 'u_translate_matrix]
                  (m/translation-matrix xoff (+ baseline yoff)))
        (assoc :baked-char baked-char))))

(defn instance-assoc-lines
  ([dynamic-entity font-entity lines]
   (instance-assoc-lines dynamic-entity font-entity lines nil))
  ([dynamic-entity font-entity lines progress]
   (let [progress    (or progress #?(:clj Integer/MAX_VALUE :cljs js/Number.MAX_SAFE_INTEGER))
         idx->ch     (->> (map-indexed (fn [line-num l] (map (fn [ch] [line-num ch]) (vec l))) lines)
                          (apply concat)
                          (map-indexed (fn [idx [line-num ch]] [idx line-num ch])))
         line->chars (->> (map (fn [[idx line-num ch]] [line-num idx (get-baked-char font-entity ch)]) idx->ch)
                          (group-by first))
         idx->xadv   (->> (update-vals line->chars
                                       #(reductions + 0 (map (fn [[_ _ baked-ch]] (:xadv baked-ch)) (drop-last %))))
                          (mapcat second)
                          (into []))]
     (loop [entity dynamic-entity [[char-idx line-num ch] & remaining] idx->ch]
       (if (and (some? char-idx) (< char-idx progress))
         (let [char-entity (-> font-entity (crop-char ch) (t/color [1 1 1 1]))
               xadv        (nth idx->xadv char-idx)
               y-total     (* line-num (-> font-entity :baked-font :font-height))]
           (recur (i/assoc entity char-idx
                           (-> char-entity
                               (update-in [:uniforms 'u_translate_matrix]
                                          #(m/multiply-matrices 3 (m/translation-matrix xadv y-total) %))))
                  remaining))
         entity)))))

(comment
  (let [_    (require '[engine.engine] '[assets.texts])
        game engine.engine/hmm-game
        {:keys [dynamic-entity font-entity]} @(:assets.texts/font-instances* game)
        size 10000
        lines [(apply str (take size (repeatedly (constantly "aa"))))
               (apply str (take size (repeatedly (constantly "bb"))))]]
    (time
     (do (instance-assoc-lines dynamic-entity font-entity lines)
         :done))))
