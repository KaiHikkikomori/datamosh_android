package ru.datamosh.radio

data class MoreItem(val title: String, val cover: Int, val url: String)

/**
 * Cards of the "Ещё от DATAMOSH" row. To add or change a card: put the cover into
 * res/drawable-nodpi (square image) and edit this list.
 */
val moreFromDatamosh = listOf(
    MoreItem(
        "DATAMOSH Vol.1",
        R.drawable.more_vol1,
        "https://bnd.lc/datamosh_vol1",
    ),
    MoreItem(
        "DATAMOSH Vol.2",
        R.drawable.more_vol2,
        "https://bnd.lc/datamosh_vol2",
    ),
    MoreItem(
        "Официальный Spotify плейлист",
        R.drawable.more_spotify,
        "https://open.spotify.com/playlist/3FHw52AQ2dEk2IYSsgRhDH?si=vuxZWjuuQl2PVYww349d7g&pi=1JkFnZQZSImNO",
    ),
)
