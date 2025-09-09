(ns rules.dialogues
  (:require
   [assets.assets :as asset]
   [assets.texts :as texts]
   [clojure.spec.alpha :as s]
   [engine.macros :refer [s->]]
   [engine.world :as world]
   [odoyle.rules :as o]
   [play-cljc.gl.core :as c]
   [play-cljc.instances :as instances]
   [play-cljc.transforms :as t]
   [rules.input :as input]
   [rules.instanceable :as instanceable]
   [rules.time :as time]))

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

(s/def ::dialogue-instances* #(instance? #?(:clj clojure.lang.Atom :cljs Atom) %))

(defn init-asset [game db*]
  (let [asset-id  :id/berkelana
        raw-image (::instanceable/raw (get @db* asset-id))
        dialogue-instanced
        (c/compile game (-> (instances/->instanced-entity raw-image)
                            (assoc :fragment dialogue-box-frag-shader)))]
    (reset! (::dialogue-instances* game)
            {::raw raw-image
             ::instanced dialogue-instanced})))

(def script
  ["it's okay now"
   "It's all gone"
   "You can be yourself again"])

(s/def ::delay-ms number?)

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
  (when (some? (::raw @(::dialogue-instances* game)))
    (let [;; require frame to be square 
          x 32 y 128
          {:keys [texts cnt]} (first (o/query-all world ::texts/counter))
          {::keys [raw instanced]} @(::dialogue-instances* game)
          width 64 height 18 pos-x 2 pos-y -30
          nine-patch (nine-patch raw camera width height (+ x pos-x) (+ y pos-y))]
      (when (> (mod cnt (inc (count texts))) 0)
        (c/render game (-> (reduce-kv instances/assoc instanced nine-patch)
                           (t/project game-width game-height)))))))

(world/system system
  {::world/init-fn
   (fn [_game world]
     (-> world
         (o/insert ::this ::delay-ms 0)
         (o/insert ::texts/test ::texts/counter 0)
         (o/insert ::texts/test ::texts/texts script)))

   ::world/rules
   (o/ruleset
    {::prep-dialogue-box
     [:what
      [:id/berkelana ::asset/loaded? true]
      [::world/global ::world/game game]
      [::asset/global ::asset/db* db*]
      :then
      (when (nil? @(::dialogue-instances* game))
        (init-asset game db*))]

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
             (o/insert ::texts/test ::texts/counter (inc counter))))]})

   ::world/render-fn render})