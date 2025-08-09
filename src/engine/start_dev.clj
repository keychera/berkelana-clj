(ns engine.start-dev
  (:require [engine.start :as start]
            [clojure.spec.test.alpha :as st]
            [play-cljc.gl.core :as pc]))

(defn start []
  (st/instrument)
  (let [window (start/->window)
        game (pc/->game (:handle window))]
    (start/start game window)))

(defn -main []
  (start))
