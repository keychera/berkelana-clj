(ns engine.refresh)

(def *refresh? (atom false))

(defn ^:dev/after-load set-refresh []
  (reset! *refresh? true))
