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
   [rules.dev.dev-only :as dev-only]
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

(defn render [world game camera game-width game-height]
  (when (some? (::raw @(::dialogue-instances* game)))
    (let [;; require frame to be square 
          x 32 y 128
          {:keys [counter]} (first (o/query-all world ::test-counter))
          {::keys [raw instanced]} @(::dialogue-instances* game)
          width 64 height 18 pos-x 2 pos-y -30
          nine-patch (nine-patch raw camera width height (+ x pos-x) (+ y pos-y))] 
      (when (not= (mod counter 3) 0)
        (c/render game (-> (reduce-kv instances/assoc instanced nine-patch)
                           (t/project game-width game-height)))))))

(def empty-text {:lines [] :x 0 :y 0})
(def text
  {:lines ["I see now that the circumstances of one's birth are irrelevant"
           "It is what you do with the gift of life that determines who you are."]
   :x 32 :y 128})

(s/def ::delay-ms number?)
(s/def ::counter int?)

(world/system system
  {::world/init-fn
   (fn [world _game]
     (-> world
         (o/insert ::this ::delay-ms 0)
         (o/insert ::mew2 ::counter 0)))

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
      :when (> delay-ms 0)
      :then (s-> session (o/insert ::this ::delay-ms (- delay-ms delta-time)))]

     ::test-counter
     [:what
      [::mew2 ::counter counter]]

     ::progress-dialogue
     [:what
      [::input/space ::input/pressed-key ::input/keydown]
      [::mew2 ::counter counter {:then false}]
      [::this ::delay-ms delay-ms {:then false}]
      :when (<= delay-ms 0)
      :then
      (s-> session
           (o/insert ::this ::delay-ms 50)
           (cond->
            :else (o/insert ::mew2 ::texts/to-render empty-text)
            (not= (mod (inc counter) 3) 0) (o/insert ::mew2 ::texts/to-render text))
           (o/insert ::mew2 ::counter (inc counter)))]})

   ::world/render-fn render})