#!/usr/bin/env -S filebot -script

// require mkvtoolnix and atomicparsley
mkvpropedit   = any{ mkvpropedit   }{ WriteTags.Command.mkvpropedit.getCommand()   }
atomicparsley = any{ atomicparsley }{ WriteTags.Command.AtomicParsley.getCommand() }

def quote(value) {
    return "\"${value}\"" // Add double quotes around the value
}

def mkv(f, m) {
    def args = ['--verbose', quote(f), '--edit', 'info', '--set', "title=${quote(m)}"]

    def xml = null
    def cover = null

    if (m instanceof Episode) {
        xml = XML {
            Tags {
                Tag {
                    Targets {
                        TargetTypeValue('70')
                    }
                    Simple {
                        Name('CONTENT_TYPE')
                        String('TV Show')
                    }
                    Simple {
                        Name('TITLE')
                        String(m.seriesName)
                    }
                    if (m.seriesInfo.database =~ /TheTVDB/) {
                        Simple {
                            Name('TVDB')
                            String(m.seriesInfo.id.pad(5))
                        }
                    }
                    if (m.seriesInfo.database =~ /TheMovieDB/) {
                        Simple {
                            Name('TMDB')
                            String('tv/' + m.seriesInfo.id)
                        }
                    }
                }
                Tag {
                    Targets {
                        TargetTypeValue('60')
                    }
                    Simple {
                        Name('PART_NUMBER')
                        String(m.season ?: 0)
                    }
                }
                Tag {
                    Targets {
                        TargetTypeValue('50')
                    }
                    Simple {
                        Name('PART_NUMBER')
                        String(m.episode ?: 0)
                    }
                    Simple {
                        Name('TITLE')
                        String(m.title)
                    }
                    Simple {
                        Name('DATE_RELEASED')
                        String(m.airdate)
                    }
                    Simple {
                        Name('GENRE')
                        String(m.seriesInfo.genres.join(';'))
                    }
                    Simple {
                        Name('PUBLISHER')
                        String(m.seriesInfo.network)
                    }
                    Simple {
                        Name('DIRECTOR')
                        String(m.info?.director)
                    }
                    Simple {
                        Name('SYNOPSIS')
                        String(m.info?.overview)
                    }
                    Simple {
                        Name('XATTR')
                        String(m.toJsonString())
                    }
                }
            }
        }
        cover = poster(m)
    }

    if (m instanceof Movie) {
        xml = XML {
            Tags {
                Tag {
                    Targets {
                        TargetTypeValue('50')
                    }
                    Simple {
                        Name('CONTENT_TYPE')
                        String('Movie')
                    }
                    Simple {
                        Name('TITLE')
                        String(m.name)
                    }
                    Simple {
                        Name('DATE_RELEASED')
                        String(m.info?.released ?: m.year)
                    }
                    Simple {
                        Name('DIRECTOR')
                        String(m.info?.director)
                    }
                    Simple {
                        Name('GENRE')
                        String(m.info?.genres.join(';'))
                    }
                    Simple {
                        Name('KEYWORDS')
                        String(m.info?.collection)
                    }
                    Simple {
                        Name('SUMMARY')
                        String(m.info?.tagline)
                    }
                    Simple {
                        Name('SYNOPSIS')
                        String(m.info?.overview)
                    }
                    if (m instanceof MoviePart) {
                        Simple {
                            Name('PART_NUMBER')
                            String(m.partIndex)
                        }
                        Simple {
                            Name('TOTAL_PARTS')
                            String(m.partCount)
                        }
                    }
                    if (m.imdbId > 0) {
                        Simple {
                            Name('IMDB')
                            String('tt' + m.imdbId.pad(7))
                        }
                    }
                    if (m.tmdbId > 0) {
                        Simple {
                            Name('TMDB')
                            String('movie/' + m.tmdbId)
                        }
                    }
                    Simple {
                        Name('XATTR')
                        String(m.toJsonString())
                    }
                }
            }
        }
        cover = poster(m)
    }

    if (xml) {
        def tags = getTemporaryFolder('tags').createFile("${m.id}.xml")

        log.finest(xml)
        xml.saveAs(tags)

        args += ['--tags', "global:${quote(tags)}"]
    }
    
    if (cover) {
        if (f.mediaInfo.General.Attachments =~ /cover.png/) {
            args += ['--attachment-name', 'cover.png', '--attachment-mime-type', 'image/png', '--replace-attachment', "name:cover.png:${quote(cover)}"]
        } else {
            args += ['--attachment-name', 'cover.png', '--attachment-mime-type', 'image/png', '--add-attachment', quote(cover)]
        }
    }

    execute(quote(mkvpropedit), *args)
}

def mp4(f, m) {
    def options = [
        '--title'       : quote(m),
        '--hdvideo'     : f.mediaCharacteristics?.height >= 1000
    ]

    if (m instanceof Episode) {
        options << [
            '--stik'        : 'TV Show',
            '--year'        : m.airdate?.toInstant(),
            '--TVShowName'  : quote(m.seriesName),
            '--TVEpisodeNum': quote(m.episode),
            '--TVSeasonNum' : quote(m.season),
            '--description' : quote(m.title),
            '--genre'       : quote(m.seriesInfo.genres.join(';')),
            '--TVNetwork'   : quote(m.seriesInfo.network),
            '--artist'      : quote(m.info?.director),
            '--longdesc'    : quote(m.info?.overview),
            '--artwork'     : quote(poster(m))
        ]
    }

    if (m instanceof Movie) {
        options << [
            '--stik'        : 'Movie',
            '--year'        : quote(m.info?.released?.toInstant() ?: m.year),
            '--artist'      : quote(m.info?.director),
            '--grouping'    : quote(m.info?.collection),
            '--genre'       : quote(m.info?.genres.join(';')),
            '--description' : quote(m.info?.tagline),
            '--longdesc'    : quote(m.info?.overview),
            '--artwork'     : quote(poster(m))
        ]
    }

    if (m instanceof MoviePart) {
        options << [
            '--disk'        : quote(m.partIndex + '/' + m.partCount)
        ]
    }

    def args = options.findAll { k, v -> v }.collectMany { k, v -> [k, v] }

    if (options.'--artwork') {
        args = ['--artwork', 'REMOVE_ALL', *args]
    }

    execute(quote(atomicparsley), quote(f), *args, '--overWrite')
}

def poster(m) {
    def url = null

    if (m instanceof Episode) {
        url = any { m.series.poster } { null }
    }
    if (m instanceof Movie) {
        url = any { m.info.poster } { null }
    }

    if (url) {
        try {
            def image = url.cache().getImage()
            if (image) {
                def file = getTemporaryFolder('tags').createFile("${m.id}.png")
                return image.saveAs(file)
            }
        } catch (e) {
            log.warning "$e.message [$url]"
        }
    }

    log.finest "[POSTER NOT FOUND] $m"
    return null
}

args.getFiles { it.video }.each { f ->
    def m = f.metadata
    if (m) {
        log.info "[TAG] Write [$m] to [$f]"
        switch (f.extension) {
            case ~/mkv/:
                mkv(f, m)
                break
            case ~/mp4|m4v/:
                mp4(f, m)
                break
            default:
                log.warning "[TAGS NOT SUPPORTED] $f"
                break
        }
    } else {
        log.finest "[XATTR NOT FOUND] $f"
    }
}
