(ns rules.dialogues
  (:require
   #?(:clj [engine.macros :refer [s->]]
      :cljs [engine.macros :refer-macros [s->]])
   [assets.assets :as asset]
   [assets.spritesheet :as spritesheet]
   [assets.texts :as texts]
   [clojure.spec.alpha :as s]
   [engine.context :as context]
   [engine.world :as world]
   [odoyle.rules :as o]
   [play-cljc.gl.core :as c]
   [play-cljc.instances :as instances]
   [play-cljc.transforms :as t]
   [rules.input :as input]
   [rules.time :as time]
   [rules.ubim :as ubim]))

(def dialogue-box-frag-shader
  {:precision "mediump float"
   :uniforms
   '{u_image sampler2D}
   :inputs
   '{v_tex_coord vec2}
   :outputs
   '{o_color vec4}
   :signatures
   '{main ([] void)}
   :functions
   '{main ([]
           (=vec4 tex (texture u_image v_tex_coord))
           (=float a (min (.a tex) 0.9))
           (= o_color (vec4 (.rgb tex) a)))}})

(defonce dialogue-instance* (atom nil))

(defn init-asset [db*]
  (let [game      @context/game*
        asset-id  :asset/berkelana
        raw-image (::spritesheet/raw (get @db* asset-id))
        dialogue-instanced
        (c/compile game (-> (instances/->instanced-entity raw-image)
                            (assoc :fragment dialogue-box-frag-shader)))]
    (reset! dialogue-instance*
            {::raw raw-image
             ::instanced dialogue-instanced})))


(def script
  ["it's okay now"
   "It's all gone"
   "You can be yourself again"])

(subvec script 0 (mod 6 (inc (count script))))

(s/def ::delay-ms number?)

(def system
  {::world/init
   (fn [world]
     (-> world
         (o/insert ::this ::delay-ms 0)
         (o/insert ::texts/test ::texts/counter 0)
         (o/insert ::texts/test ::texts/texts script)))

   ::world/rules
   (o/ruleset
    {::prep-dialogue-box
     [:what
      [:asset/berkelana ::asset/loaded? true]
      [::asset/global ::asset/db* db*]
      :then
      (when (nil? @dialogue-instance*)
        (init-asset db*))]

     ::progress-delay
     [:what
      [::time/now ::time/delta delta-time]
      [::this ::delay-ms delay-ms {:then false}]
      :when
      (> delay-ms 0)
      :then (s-> session (o/insert ::this ::delay-ms (- delay-ms delta-time)))]

     ::progress-dialogue
     [:what
      [::input/space ::input/pressed-key ::input/keydown]
      [::texts/test ::texts/counter counter {:then false}]
      [::this ::delay-ms delay-ms {:then false}]
      :then
      (when (<= delay-ms 0)
        (s-> session
             (o/insert ::this ::delay-ms 50)
             (o/insert ::texts/test ::texts/counter (inc counter))))
      (println "hey" counter)]})})

(type (::world/rules system))

(defn nine-patch [raw camera width height pos-x pos-y]
  (let [frame-index 48 frame-w 32  inset 5 ;; hardcoded for now, or forever
        frames-per-row (/ (:width raw) frame-w)
        frame-x (mod frame-index frames-per-row)
        frame-y (quot frame-index frames-per-row)
        crop-x (* frame-x frame-w)
        crop-y (* frame-y frame-w)
        raw  (t/invert raw camera)
        center (-> raw
                   (t/translate (+ inset pos-x) (+ inset pos-y))
                   (t/scale width height)
                   (t/crop (+ crop-x inset) (+ crop-y inset)
                           (- frame-w (* inset 2))
                           (- frame-w (* inset 2))))
        left   (-> raw
                   (t/translate (+ pos-x) (+ inset pos-y))
                   (t/scale inset height)
                   (t/crop crop-x (+ crop-y inset)
                           inset (- frame-w (* inset 2))))
        right  (-> raw
                   (t/translate (+ width inset pos-x) (+ inset pos-y))
                   (t/scale inset height)
                   (t/crop (+ crop-x frame-w (- inset)) (+ crop-y inset)
                           inset (- frame-w (* inset 2))))
        up     (-> raw
                   (t/translate (+ inset pos-x) (+ pos-y))
                   (t/scale width inset)
                   (t/crop (+ crop-x inset) crop-y
                           (- frame-w (* inset 2)) inset))
        down   (-> raw
                   (t/translate (+ inset pos-x) (+ height inset pos-y))
                   (t/scale width inset)
                   (t/crop (+ crop-x inset) (+ crop-y frame-w (- inset))
                           (- frame-w (* inset 2)) inset))
        l+u     (-> raw
                    (t/translate (+ pos-x) (+ pos-y))
                    (t/scale inset inset)
                    (t/crop crop-x crop-y inset inset))
        u+r     (-> raw
                    (t/translate (+ width inset pos-x) (+ pos-y))
                    (t/scale inset inset)
                    (t/crop (+ crop-x frame-w (- inset)) crop-y inset inset))
        r+d     (-> raw
                    (t/translate (+ width inset pos-x) (+ height inset pos-y))
                    (t/scale inset inset)
                    (t/crop (+ crop-x frame-w (- inset)) (+ crop-y frame-w (- inset)) inset inset))
        d+l    (-> raw
                   (t/translate (+ pos-x) (+ height inset pos-y))
                   (t/scale inset inset)
                   (t/crop crop-x (+ crop-y frame-w (- inset)) inset inset))]
    [center left l+u up u+r right r+d down d+l]))

(defn render [game world camera game-width game-height]
  (when (some? (::raw @dialogue-instance*))
    (let [;; require frame to be square 
          {:keys [x y]} (first (o/query-all world ::ubim/ubim-esse))
          {:keys [texts cnt]} (first (o/query-all world ::texts/counter))
          {::keys [raw instanced]} @dialogue-instance*
          width 64 height 18 pos-x 2 pos-y -30
          nine-patch (nine-patch raw camera width height (+ x pos-x) (+ y pos-y))]
      (when (> (mod cnt (inc (count texts))) 0)
        (c/render game (-> (reduce-kv instances/assoc instanced nine-patch)
                           (t/project game-width game-height)))))))
 